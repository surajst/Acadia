import React, { useCallback, useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TextInput, TouchableOpacity, Alert, ActivityIndicator,
} from 'react-native';
import { Stack, useRouter } from 'expo-router';
import { getTeacherClasses, createTeacherTask, searchMyStudents, getSubjects } from '@/services/api';
import T from '@/constants/theme';

/**
 * Assigning work from the phone.
 *
 * The web console has had this form since the beginning -- title, subject,
 * type, due date, XP, and whether it goes to a whole class or one child. The
 * app could only list tasks that already existed, which is also why the
 * student's Challenges tab was always empty: nothing could put anything there.
 */

const TYPES = ['HOMEWORK', 'PRACTICE', 'PROJECT', 'READING'] as const;
type TaskType = typeof TYPES[number];

const pad = (n: number) => String(n).padStart(2, '0');
const iso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const pretty = (d: Date) =>
  d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });

export default function NewTaskScreen() {
  const router = useRouter();

  const [classes, setClasses] = useState<any[]>([]);
  const [subjects, setSubjects] = useState<any[]>([]);
  const [cls, setCls] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState<TaskType>('HOMEWORK');
  const [xp, setXp] = useState('50');
  const [dueOffset, setDueOffset] = useState(1);

  const [toClass, setToClass] = useState(true);
  const [studentQuery, setStudentQuery] = useState('');
  const [matches, setMatches] = useState<any[]>([]);
  const [student, setStudent] = useState<any | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const [list, subs] = await Promise.all([
          getTeacherClasses(),
          getSubjects().catch(() => []),
        ]);
        setClasses(list);
        setSubjects(Array.isArray(subs) ? subs : []);
        if (list.length > 0) setCls(list[0]);
      } catch { /* the empty state below covers it */ }
      finally { setLoading(false); }
    })();
  }, []);

  // Search runs against the teacher's own students only -- the endpoint scopes
  // to their sections, so there is no way to assign to someone else's class.
  const runSearch = useCallback(async (q: string) => {
    setStudentQuery(q);
    setStudent(null);
    if (q.trim().length < 2) { setMatches([]); return; }
    try { setMatches((await searchMyStudents(q.trim())).slice(0, 6)); }
    catch { setMatches([]); }
  }, []);

  const due = new Date();
  due.setDate(due.getDate() + dueOffset);

  const canSave = !!cls && title.trim().length > 0 && (toClass || !!student) && !saving;

  const submit = async () => {
    if (!canSave || !cls) return;
    setSaving(true);
    try {
      // The class carries the assignment's free-text subject name
      // ("Mathematics"); tasks are keyed by the catalogue code
      // ("MATHEMATICS"), which is what the web form sends.
      const match = subjects.find(
        (x) => String(x.displayName ?? '').toLowerCase() === String(cls.subject ?? '').toLowerCase(),
      );

      await createTeacherTask({
        title: title.trim(),
        description: description.trim(),
        subjectCode: match?.code ?? cls.subject,
        taskType: type,
        standard: cls.standard,
        assignedToClass: toClass,
        studentId: toClass ? null : student?.id,
        xpReward: parseInt(xp, 10) || 0,
        dueDate: iso(due),
      });
      Alert.alert(
        'Task assigned',
        toClass
          ? `${title.trim()} went to everyone in ${cls.gradeName ?? cls.className}.`
          : `${title.trim()} went to ${student?.name}.`,
        [{ text: 'Done', onPress: () => router.back() }],
      );
    } catch (e: any) {
      Alert.alert('Could not assign', e?.response?.data?.error ?? 'Please try again.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={s.centre}>
        <Stack.Screen options={{ title: 'New task' }} />
        <ActivityIndicator color={T.brand} />
      </View>
    );
  }

  if (classes.length === 0) {
    return (
      <View style={s.centre}>
        <Stack.Screen options={{ title: 'New task' }} />
        <Text style={s.emptyTitle}>No classes assigned</Text>
        <Text style={s.emptyBody}>Once an admin assigns you a class you can set work for it here.</Text>
      </View>
    );
  }

  return (
    <ScrollView style={s.page} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
      <Stack.Screen options={{ title: 'New task' }} />

      <Field label="TITLE">
        <TextInput
          style={s.input}
          value={title}
          onChangeText={setTitle}
          placeholder="Fractions worksheet 4"
          placeholderTextColor={T.text4}
        />
      </Field>

      <Field label="DESCRIPTION" hint="Optional">
        <TextInput
          style={[s.input, s.multiline]}
          value={description}
          onChangeText={setDescription}
          placeholder="What should they do?"
          placeholderTextColor={T.text4}
          multiline
        />
      </Field>

      <Field label="CLASS">
        <ChipRow
          items={classes.map((c) => ({ key: c.id, label: `${c.className} · ${c.subject}` }))}
          selected={cls?.id}
          onSelect={(id) => setCls(classes.find((c) => c.id === id))}
        />
      </Field>

      <Field label="TYPE">
        <ChipRow
          items={TYPES.map((t) => ({ key: t, label: t.charAt(0) + t.slice(1).toLowerCase() }))}
          selected={type}
          onSelect={(k) => setType(k as TaskType)}
        />
      </Field>

      <View style={s.row}>
        <View style={{ flex: 1 }}>
          <Field label="XP REWARD">
            <TextInput
              style={s.input}
              value={xp}
              onChangeText={(v) => setXp(v.replace(/[^0-9]/g, ''))}
              keyboardType="number-pad"
              maxLength={4}
            />
          </Field>
        </View>
        <View style={{ flex: 1.4 }}>
          <Field label="DUE">
            <ChipRow
              items={[
                { key: '0', label: 'Today' },
                { key: '1', label: 'Tomorrow' },
                { key: '7', label: 'In a week' },
              ]}
              selected={String(dueOffset)}
              onSelect={(k) => setDueOffset(Number(k))}
            />
          </Field>
        </View>
      </View>
      <Text style={s.dueNote}>Due {pretty(due)}</Text>

      <Field label="ASSIGN TO">
        <ChipRow
          items={[
            { key: 'class', label: `Whole of ${cls?.gradeName ?? 'the grade'}` },
            { key: 'one', label: 'One student' },
          ]}
          selected={toClass ? 'class' : 'one'}
          onSelect={(k) => setToClass(k === 'class')}
        />
        {/* Tasks are keyed by grade, not section -- a class task reaches 6-A,
            6-B and 6-C alike. Naming the section here would be a lie. */}
        {toClass && cls && (
          <Text style={s.dueNote}>
            Every section of {cls.gradeName ?? 'this grade'}, not just {cls.className}.
          </Text>
        )}
      </Field>

      {!toClass && (
        <View style={{ gap: 8 }}>
          <TextInput
            style={s.input}
            value={student ? student.name : studentQuery}
            onChangeText={runSearch}
            placeholder="Start typing a name…"
            placeholderTextColor={T.text4}
            autoCorrect={false}
          />
          {matches.map((m) => (
            <TouchableOpacity
              key={m.id}
              style={s.match}
              onPress={() => { setStudent(m); setMatches([]); }}
            >
              <Text style={s.matchName}>{m.name}</Text>
              <Text style={s.matchClass}>{m.className}</Text>
            </TouchableOpacity>
          ))}
        </View>
      )}

      <TouchableOpacity
        style={[s.submit, !canSave && s.submitOff]}
        onPress={submit}
        disabled={!canSave}
        accessibilityRole="button"
      >
        <Text style={[s.submitText, !canSave && s.submitTextOff]}>
          {saving ? 'Assigning…' : 'Assign task'}
        </Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <View style={{ gap: 7 }}>
      <View style={s.labelRow}>
        <Text style={s.label}>{label}</Text>
        {hint && <Text style={s.hint}>{hint}</Text>}
      </View>
      {children}
    </View>
  );
}

function ChipRow({ items, selected, onSelect }: {
  items: { key: string; label: string }[];
  selected?: string;
  onSelect: (key: string) => void;
}) {
  return (
    <View style={s.chipRow}>
      {items.map((it) => {
        const on = it.key === selected;
        return (
          <TouchableOpacity
            key={it.key}
            style={[s.chip, on && s.chipOn]}
            onPress={() => onSelect(it.key)}
            accessibilityRole="button"
            accessibilityState={{ selected: on }}
          >
            <Text style={[s.chipText, on && s.chipTextOn]}>{it.label}</Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: T.bg },
  content: { padding: 20, paddingBottom: 40, gap: 18 },
  centre: { flex: 1, backgroundColor: T.bg, alignItems: 'center', justifyContent: 'center', padding: 32, gap: 6 },
  emptyTitle: { fontSize: 15, fontWeight: '700', color: T.text },
  emptyBody: { fontSize: 13.5, color: T.text3, textAlign: 'center', lineHeight: 20 },

  labelRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  label: { fontSize: 11, fontWeight: '800', letterSpacing: 1, color: T.text3 },
  hint: { fontSize: 11, fontWeight: '500', color: T.text3 },

  input: {
    minHeight: 46, borderRadius: 14,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
    paddingHorizontal: 14, paddingVertical: 12,
    fontSize: 14.5, color: T.text,
  },
  multiline: { minHeight: 78, textAlignVertical: 'top' },

  row: { flexDirection: 'row', gap: 12 },
  dueNote: { fontSize: 12, color: T.text3, marginTop: -10 },

  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    minHeight: 40, justifyContent: 'center',
    paddingHorizontal: 14, borderRadius: T.pill,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  chipOn: { backgroundColor: T.brand50, borderColor: T.brand },
  chipText: { fontSize: 13, fontWeight: '600', color: T.text2 },
  chipTextOn: { color: T.brand },

  match: {
    padding: 12, borderRadius: 12,
    backgroundColor: T.surface, borderWidth: 1, borderColor: T.line,
  },
  matchName: { fontSize: 14, fontWeight: '600', color: T.text },
  matchClass: { fontSize: 12, color: T.text3, marginTop: 1 },

  submit: {
    minHeight: 50, borderRadius: 14, marginTop: 6,
    alignItems: 'center', justifyContent: 'center', backgroundColor: T.brand,
  },
  submitOff: { backgroundColor: T.track },
  submitText: { fontSize: 15, fontWeight: '700', color: T.onBrand },
  submitTextOff: { color: T.text3 },
});
