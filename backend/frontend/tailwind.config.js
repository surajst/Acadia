/** Scans the Thymeleaf templates (including their inline <script> blocks,
 *  which build class strings for JS-rendered rows) for every Tailwind
 *  utility actually used, and emits only those classes. */
module.exports = {
  content: ["../src/main/resources/templates/**/*.html"],
  theme: { extend: {} },
  plugins: [],
};
