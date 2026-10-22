/**
 * ACADIA demo requests -> HubSpot.
 *
 * Cloudflare Pages Function at POST /api/inquiry. For each request it:
 *   1. finds or creates the contact (by email when given, else by phone)
 *   2. finds or creates the school as a company
 *   3. creates a deal in the ACADIA pipeline's first stage, owned by Suraj,
 *      associated with the contact and the school
 *
 * Environment (Cloudflare Pages -> Settings -> Variables and secrets):
 *   HUBSPOT_TOKEN     secret. Private app token with scopes:
 *                     crm.objects.contacts.read/write, crm.objects.companies.read/write,
 *                     crm.objects.deals.read/write, crm.schemas.contacts.read/write
 *   HUBSPOT_OWNER_ID  owner for new deals (Suraj = 98249294)
 *   HUBSPOT_PIPELINE  deal pipeline id (default "default")
 *   HUBSPOT_STAGE     first stage id  (default "appointmentscheduled")
 *
 * With no token configured it answers 503, and the page's email fallback still delivers.
 */

const API = "https://api.hubapi.com";
const GROUP = "acadia";
// Contact properties for ACADIA leads, kept apart from the French site's "concept_*" ones.
const PROPS = {
  acadia_lead: ["ACADIA: lead", "bool", "booleancheckbox", "Came in through the ACADIA website"],
  acadia_role: ["ACADIA: role", "string", "text", "Principal, owner, administrator…"],
  acadia_students: ["ACADIA: school size", "string", "text", "Number of students, as chosen on the form"],
  acadia_heard_from: ["ACADIA: heard about us", "string", "text", "Answer to 'How did you hear about us?'"],
  acadia_utm_source: ["ACADIA: UTM source", "string", "text", "First visit"],
  acadia_utm_campaign: ["ACADIA: UTM campaign", "string", "text", "First visit"],
  acadia_referrer: ["ACADIA: referrer", "string", "text", "Site that sent them on their first visit"],
  acadia_inquiry_date: ["ACADIA: last inquiry", "datetime", "date", "When they last requested a demo"],
};

const clean = (v, max = 200) => String(v ?? "").replace(/[\u0000-\u001f]/g, " ").trim().slice(0, max);
const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });

class Hub {
  constructor(token) { this.token = token; }
  async call(method, path, body, ok = [200, 201, 207]) {
    const r = await fetch(API + path, {
      method,
      headers: { authorization: `Bearer ${this.token}`, "content-type": "application/json" },
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await r.text();
    if (!ok.includes(r.status)) throw new Error(`HubSpot ${method} ${path} -> ${r.status}: ${text.slice(0, 300)}`);
    return text ? JSON.parse(text) : {};
  }
  async ensureSchema() {
    await this.call("POST", "/crm/v3/properties/contacts/groups", { name: GROUP, label: "ACADIA", displayOrder: -1 }, [200, 201, 409]);
    const have = await this.call("POST", "/crm/v3/properties/contacts/batch/read",
      { inputs: Object.keys(PROPS).map((name) => ({ name })), archived: false });
    const existing = new Set((have.results || []).map((p) => p.name));
    const missing = Object.entries(PROPS).filter(([n]) => !existing.has(n)).map(([name, [label, type, fieldType, description]]) => {
      const p = { name, label, type, fieldType, groupName: GROUP, description };
      if (type === "bool") p.options = [{ label: "Yes", value: "true", displayOrder: 0 }, { label: "No", value: "false", displayOrder: 1 }];
      return p;
    });
    if (missing.length) await this.call("POST", "/crm/v3/properties/contacts/batch/create", { inputs: missing });
  }
  async findOne(object, property, value) {
    if (!value) return null;
    const r = await this.call("POST", `/crm/v3/objects/${object}/search`, {
      filterGroups: [{ filters: [{ propertyName: property, operator: "EQ", value }] }], limit: 1, properties: [property],
    });
    return r.results && r.results[0] ? r.results[0].id : null;
  }
}

let schemaReady = false;

export async function onRequestPost({ request, env }) {
  if (!env.HUBSPOT_TOKEN) return json({ ok: false, error: "crm_not_configured" }, 503);

  let d;
  try { d = await request.json(); } catch { return json({ ok: false, error: "bad_json" }, 400); }
  if (d._honey) return json({ ok: true }); // bots fill the hidden field

  const name = clean(d.name, 120), school = clean(d.school, 160), phone = clean(d.phone, 40);
  const email = clean(d.email, 160).toLowerCase();
  if (!name || !school || !phone) return json({ ok: false, error: "missing_fields" }, 400);
  if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return json({ ok: false, error: "bad_email" }, 400);

  const [firstname, ...rest] = name.split(/\s+/);
  const lastname = rest.join(" ");
  const city = clean(d.city, 80), role = clean(d.role, 60), students = clean(d.students, 40), heard = clean(d.heard, 60);
  const now = Date.now();

  const hub = new Hub(env.HUBSPOT_TOKEN);
  try {
    if (!schemaReady) { await hub.ensureSchema(); schemaReady = true; }

    // 1. contact
    const contactProps = {
      firstname, lastname, phone, company: school, jobtitle: role, city,
      acadia_lead: "true", acadia_role: role, acadia_students: students, acadia_heard_from: heard,
      acadia_inquiry_date: String(now),
    };
    const firstTouch = d.first_touch || {};
    if (firstTouch.utm_source) contactProps.acadia_utm_source = clean(firstTouch.utm_source, 100);
    if (firstTouch.utm_campaign) contactProps.acadia_utm_campaign = clean(firstTouch.utm_campaign, 100);
    if (firstTouch.referrer) contactProps.acadia_referrer = clean(firstTouch.referrer, 200);

    let contactId;
    if (email) {
      const r = await hub.call("POST", "/crm/v3/objects/contacts/batch/upsert",
        { inputs: [{ idProperty: "email", id: email, properties: { email, ...contactProps } }] });
      contactId = r.results[0].id;
    } else {
      contactId = await hub.findOne("contacts", "phone", phone);
      if (contactId) await hub.call("PATCH", `/crm/v3/objects/contacts/${contactId}`, { properties: contactProps });
      else contactId = (await hub.call("POST", "/crm/v3/objects/contacts", { properties: contactProps })).id;
    }

    // 2. school as a company
    let companyId = await hub.findOne("companies", "name", school);
    if (!companyId) companyId = (await hub.call("POST", "/crm/v3/objects/companies", { properties: { name: school, city } })).id;
    await hub.call("PUT", `/crm/v4/objects/contacts/${contactId}/associations/default/companies/${companyId}`, null, [200, 201]);

    // 3. the deal
    const description = [
      `Demo request from the website on ${new Date(now).toISOString().slice(0, 10)}`,
      `Contact: ${name}${role ? `, ${role}` : ""}`,
      `Phone / WhatsApp: ${phone}${email ? ` · Email: ${email}` : ""}`,
      `Students: ${students || "not given"} · City: ${city || "not given"}`,
      `Heard about us: ${heard || "not given"}`,
    ].join("\n");
    const deal = await hub.call("POST", "/crm/v3/objects/deals", {
      properties: {
        dealname: `${school} · ACADIA`,
        pipeline: env.HUBSPOT_PIPELINE || "default",
        dealstage: env.HUBSPOT_STAGE || "appointmentscheduled",
        hubspot_owner_id: env.HUBSPOT_OWNER_ID || "",
        description,
      },
      associations: [
        { to: { id: contactId }, types: [{ associationCategory: "HUBSPOT_DEFINED", associationTypeId: 3 }] },
        { to: { id: companyId }, types: [{ associationCategory: "HUBSPOT_DEFINED", associationTypeId: 5 }] },
      ],
    });
    return json({ ok: true, deal: deal.id });
  } catch (e) {
    console.error(String(e));
    return json({ ok: false, error: "crm_error" }, 502);
  }
}

export function onRequest() {
  return json({ ok: false, error: "method_not_allowed" }, 405);
}
