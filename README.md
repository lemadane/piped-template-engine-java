# Piped Template Engine (PTE)

[![JitPack](https://jitpack.io/v/lemadane/piped-template-engine.svg)](https://jitpack.io/#lemadane/piped-template-engine)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://www.oracle.com/java/)

**Piped Template Engine (PTE)** is a modern, light-weight, and ultra-high-performance server-side HTML template engine for Java and Spring Boot. It uses a unique pipe-based syntax (`|var|`) designed to keep HTML code readable, natural, and free of clutter.

```html
<h1>|title|</h1>
<p>Hello, |user?.profile?.displayName ?? 'Guest'|</p>
```

---

##  Why Use Piped Template Engine?

1. **Native Bytecode Performance**: PTE transpiles template AST trees into Java source and compiles them **in-memory** to live `.class` JVM bytecode. It runs at native JVM speed, matching **JTE** and executing **3x–8x faster than Thymeleaf** with zero disk I/O.
2. **Rich Control Flow**: First-class support for collection loops (`|each|`), auto-directional numeric range loops (`|for i from 1 to 10 step 2|`), loop control flow (`|continue|`, `|break|`), and empty-state `|else|` fallback blocks.
3. **SvelteKit-Style Routing**: Stop writing boilerplate Java `@Controller` mappings just to load static pages. PTE registers routes automatically from your directory structure.
4. **Built for HTMX**: Render specific page zones dynamically using inline **Fragments**, compile clean **Target DOM IDs** using the slug filter, and perform out-of-band updates with zero friction.
5. **Secure by Default**: Automatically escapes all variables to defend against Cross-Site Scripting (XSS) attacks.

---

##  How to Use PTE in Spring Boot

### 1. Add Dependencies
Configure your `build.gradle` to fetch PTE from **JitPack**:

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Spring Boot Starter (auto-configures view mapping)
    implementation 'com.github.lemadane.piped-template-engine:piped-template-engine-spring-boot-starter:v0.1.1'
}
```

### 2. Configure Properties
Add your template location rules in `src/main/resources/application.properties`:

```properties
# Custom prefix folder (Defaults to pte-templates)
piped.template.prefix=src/main/resources/pte-templates
piped.template.suffix=.pte
```

### 3. File-Based Routing vs. Controllers
PTE lets you use both standard Spring MVC controllers and automatic file-based routes:

* **Option A: SvelteKit-Style File-Based Routing (Zero Java Code)**
  Create `src/main/resources/pte-routes/about/+page.pte`. Navigating to `/about` will render it instantly.
* **Option B: Spring MVC Controllers**
  ```java
  @Controller
  public class WebController {
      @GetMapping("/")
      public String home(Model model) {
          model.addAttribute("title", "Dashboard");
          return "pages/home"; // Resolves to pte-templates/pages/home.pte
      }
  }
  ```

---

##  Feature Guide & Code Samples

Here is a complete list of all template features supported in PTE since day one:

---

### 1. HTML Escaping (Default Output)
Automatically filters output values to prevent Cross-Site Scripting (XSS) injections.

```html
<!-- Renders: &lt;b&gt;Hello&lt;/b&gt; -->
<p>|user.bio|</p>
```

---

### 2. Raw / Trusted HTML Output (`|html expr|`)
Bypasses default HTML escaping to output raw markup safely.

```html
<!-- Renders: <b>Welcome back!</b> -->
<div>|html user.signature|</div>
```

---

### 3. HTML Attribute Escaping (`|attr expr|`)
Escapes characters specifically for injection safety inside HTML attributes.

```html
<!-- Prevents breaking out of double quotes -->
<input value="|attr user.name|">
```

---

### 4. JSON & URL Encoding (`|json expr|` / `|url expr|`)
Formats output for JavaScript blocks and query parameter safety.

```html
<!-- JSON Escaping -->
<script>var settings = |json user.settings|;</script>

<!-- URL Escaping -->
<a href="/search?q=|url query|">Search</a>
```

---

### 5. Optional Chaining & Null Safety
Safely navigate deeply nested object properties without throwing `NullPointerException`.

```html
<!-- Safe optional navigation with ?? fallback value -->
<p>Welcome back, |user?.profile?.nickname ?? 'Guest'|</p>
```

---

### 6. Ternary Conditional Operator
Clean inline branching syntax directly inside expressions and attribute values.

```html
<!-- Dynamic status class assignment -->
<div class="|task.completed ? 'is-complete' : 'is-pending'|">
    |task.title|
</div>
```

---

### 7. Conditional Attribute Shorthand
Cleanly attach boolean attributes like `checked`, `disabled`, or `selected` without printing empty properties.

```html
<!-- Renders checked only if true; otherwise prints nothing -->
<input type="checkbox" |attr checked if task.completed|>
```

---

### 8. If / Else-If / Else Conditionals
Standard blocks for rendering structural template changes.

```html
|if user.role == 'ADMIN'|
    <span class="badge admin">Administrator</span>
|else-if user.role == 'MANAGER'|
    <span class="badge manager">Manager</span>
|else|
    <span class="badge user">User</span>
|/if|
```

---

### 9. Switch Blocks
Efficient multi-branch switch statements. Supports explicit `fallthrough`.

```html
|switch task.priority|
    |case 'HIGH'|
        <div class="priority-red">Urgent</div>
    |case 'MEDIUM'|
        <div class="priority-yellow">Important</div>
    |default|
        <div class="priority-green">Standard</div>
|/switch|
```

---

### 10. Collection Loops (`|each|`)
Loop over Collections, Sets, Maps, and Arrays. Provides an empty-state `|else|` block that executes when the collection is empty or null.

```html
<ul>
|each item in taskList|
    <li>|item.title|</li>
|else|
    <li>No tasks found.</li>
|/each|
</ul>
```

---

### 11. Range-Based Loops (`|for|`)
Loop over numeric integer ranges with automatic direction detection (ascending or descending) and optional step distance expressions.

```html
<!-- Ascending Range (1 to 5) -->
|for i from 1 to 5|
    <span>|i|</span>
|/for|

<!-- Descending Range with Step (10 down to 1 step 2 -> 10, 8, 6, 4, 2) -->
|for i from 10 to 1 step 2|
    <span>|i|</span>
|/for|

<!-- Expression Boundaries & Empty-State Else -->
|for i from start to end step interval|
    <span>|i|</span>
|else|
    <p>Range is empty.</p>
|/for|
```

* **Direction Rules**: Ascending when `start < end`, descending when `start > end`, 1 iteration when `start == end`.
* **Step**: Defaults to `1`. Must evaluate to a positive integer.
* **Scoping**: The loop variable has block scope and is available only inside the loop body.

---

### 12. Control Flow Directives (`|continue|` and `|break|`)
Control loop iterations dynamically within `for` and `each` loops. Supported inside nested `if`, `switch`, and other directives.

```html
|for i from 1 to 10|
    |if i == 5|
        |continue| <!-- Skip remainder of current iteration -->
    |/if|
    |if i == 8|
        |break|    <!-- Terminate loop immediately -->
    |/if|
    <span>|i|</span>
|/for|
```

* **`|continue|`**: Skips rendering content after it in the current iteration and advances to the next iteration of the nearest enclosing loop.
* **`|break|`**: Immediately terminates the nearest enclosing loop. Breaking an inner loop leaves outer loops unaffected.
* **Empty-State `|else|`**: Executes only when the loop performs zero iterations. If a loop executes at least one iteration (even if terminated early by `|break|` or skipping items via `|continue|`), the `|else|` block does not render.

---

### 13. Loop Metadata
Access iteration state properties (`index`, `count`, `first`, `last`, `total`) using the local `each` scope inside any `each` loop.

```html
|each item in items|
    <div class="|each.first ? 'header-item' : ''|">
        Item |each.count| of |each.total|: |item.name|
    </div>
|/each|
```

---

### 14. Loop Separators
Render delimiters (like commas, breadcrumb symbols, or HTML dividers) between loop iterations automatically, skipping the final item.

```html
<!-- Output: HTML / CSS / JS -->
|each skill in skills||skill||separator| / |/separator||/each|
```

---

### 15. SvelteKit-Style File-Based Routing
Zero-code web endpoints generated directly from your directory tree hierarchy, automatically injecting path and query variables.

**Template Path (`src/main/resources/pte-routes/posts/[id]/+page.pte`)**:
```html
<div>
    <h1>Post Details</h1>
    <p>Viewing Post ID: |id|</p>
</div>
```

---

### 16. Layouts & Yield Sections
Wrap pages inside master templates to reuse headers, sidebars, and scripts.

**Layout File (`layouts/main.pte`)**:
```html
<html>
<head>
    <title>|yield title|</title>
</head>
<body>
    <main>|yield content|</main>
</body>
</html>
```

**Page File (`pages/home.pte`)**:
```html
|layout layouts/main|
|section title| Dashboard Page |/section|
|section content|
    <h1>Welcome User</h1>
|/section|
```

---

### 17. Components & Named Slots
Define highly reusable interface widgets and pass them rich nested markup slots.

**Component File (`components/card.pte`)**:
```html
<div class="card">
    <div class="card-header">|slot header|</div>
    <div class="card-body">|slot body|</div>
</div>
```

**Usage Page (`pages/dashboard.pte`)**:
```html
|component components/card|
    |slot header|
        <h3>System Stats</h3>
    |/slot|
    |slot body|
        <p>All servers online.</p>
    |/slot|
|/component|
```

---

### 18. Includes
Include simple partial template files directly. Supports passing sub-models using the `with` statement.

```html
<!-- Include header and pass navigation list object -->
|include partials/navbar with navItems|
```

---

### 19. Template-Defined Macros
Define reusable markup function helpers directly inside your templates or utility files.

```html
<!-- Define Macro -->
|macro action_button(label, color)|
    <button style="background-color: |color|; border-radius: 4px;">|label|</button>
|/macro|

<!-- Call Macro -->
|call action_button('Delete Item', '#ff3860')|
```

---

### 20. Inline Template Fragments
Target and render specific subsections of a template. Excellent for returning lightweight HTML payloads for HTMX updates.

**Template File (`pages/tasks.pte`)**:
```html
<div>
    <h1>Tasks</h1>
    |fragment list-zone|
        <ul id="task-list">
            <li>Buy milk</li>
        </ul>
    |/fragment|
</div>
```

**Java Controller Invocation**:
```java
// Renders only the <ul> block, skipping the surrounding headers!
String html = templateEngine.renderFragment("pages/tasks", "list-zone", model);
```

---

### 21. Strongly Typed Models
Explicitly declare your page model type at the top of templates.

```html
|model com.example.model.TaskPageModel|

<h1>|model.pageTitle|</h1>
<p>Due Date: |model.dueDate|</p>
```

---

### 22. Built-in Pipe Filters
Apply formatter transformations directly to variable output expressions.

```html
<p>User: |name, lower, capitalize|</p> <!-- 'ALICE' -> 'Alice' -->
<p>Slug: |title, slug|</p>               <!-- 'First Post!' -> 'first-post' -->
<p>Cost: |price, currency 'EUR'|</p>    <!-- 15.5 -> '15.50 €' -->
```

---

### 23. Conditional Attribute Whitespace Cleanup
PTE parses surrounding tags and automatically cleans up extra trailing/double whitespaces if a conditional attribute evaluates to false.

```html
<!-- If completed is false, output is cleaned up to: <input class="form-input"> -->
<!-- No trailing or double spacing is left in the HTML output! -->
<input class="form-input" |attr checked if completed|>
```

---

### 24. Circular Include Detection
PTE tracks the active include stack at render-time and throws a compile-time exception if a template attempts to include itself recursively.

```html
<!-- If templates/index.pte includes partials/navbar.pte -->
<!-- And partials/navbar.pte includes templates/index.pte -->
<!-- PTE throws: circular include detected: index -> navbar -> index -->
|include partials/navbar|
```

---

### 25. HTML Minification & Prettifying
Compress raw templates using the block-level `|minify|` tag, or configure the engine globally to minify or prettify (beautify) all rendered page sources automatically.

```html
<!-- Inline Block Minification -->
|minify|
    <div class="row">
        <span>Compressed Text</span>
    </div>
|/minify|
```

Invoke via Java API:
```java
// Globally minify all templates (collapses comments/whitespaces at compile time)
templateEngine.setMinify(true);

// Globally format/indent HTML output
templateEngine.setPrettify(true);
```

---

### 26. Template Comments
Write developer comments inside templates that are completely stripped out at compile time and never outputted to the user's browser.

```html
<!-- Single-line comment -->
|# This comment will not render |

<!-- Multi-line block comment -->
|# 
   This is a block comment.
   It spans multiple lines.
#|
```

---

### 27. Route-Level Page Options & Metadata
Configure route-level page properties (`title`, `cache`, `auth`, `roles`, `contentType`) directly inside the template. The Spring Boot starter automatically resolves and enforces these configurations.

```html
|page title = "System Settings"|
|page cache = "public, max-age=3600"|
|page auth = true|
|page roles = ["ADMIN"]|
|page contentType = "text/html;charset=UTF-8"|

<h1>Settings Page</h1>
```

---

### 28. Request-Scoped Page Context
Access implicit HTTP request metadata properties (`requestUri`, `method`, `headers`, `params`, `session`) out-of-the-box inside any layout page using the `page` context map.

```html
<!-- Highlight active tab depending on the request URI -->
<nav>
    <a href="/settings" class="|page.requestUri == '/settings' ? 'active' : ''|">Settings</a>
</nav>
```

---

### 29. Recoverable Rendering (Attempt & Recover)
Wrap error-prone layout widgets, database-backed components, or custom scripts inside attempt blocks. If rendering fails, PTE rolls back/discards all partial HTML output generated inside the block, captures the error description into a named variable, and renders the recover fallback block instead.

```html
|attempt|
    <!-- If sales-chart fails or throws an exception, all inner markup is rolled back -->
    |component "components/sales-chart" with { report: report }|
    |/component|
|recover as error_detail|
    <div class="notification is-warning">
        The sales chart is temporarily unavailable. Details: |error_detail|
    </div>
|/attempt|
```

---

### 30. Progressive Web App (PWA) Meta & Service Worker Tag
Abstract mobile viewports, theme colors, iOS app capability, icons, web app manifest links, and service worker registration into a single inline tag:

```html
<head>
    <title>|title ?? 'My App'|</title>
    |pwa name='TaskMaster' theme='#4f46e5' icon='/icon-192.png' manifest='/manifest.json' sw='/sw.js'|
</head>
```

---

### 31. HTMX Head & Element Attribute Tags
Abstract HTMX library scripts, extension plugins, indicator CSS rules, and HTMX element action attributes into single inline tags.

#### A. HTMX Head Setup Tag (`|htmx ...|`)
```html
<head>
    <!-- Loads HTMX library, json-enc & sse extensions, and default loading indicator CSS -->
    |htmx src='/js/htmx.min.js' ext='json-enc,sse' indicator=true|
</head>
```

#### B. HTMX Element Action Shorthand Tags
Write HTMX attribute bindings using `|htmx-get ...|`, `|htmx-post ...|`, `|htmx-put ...|`, `|htmx-delete ...|`, `|htmx-patch ...|`:
```html
<!-- GET request -->
<button |htmx-get '/api/tasks' target='#task-list' swap='outerHTML' indicator='#spinner'|>
    Refresh Tasks
</button>

<!-- POST request -->
<form |htmx-post '/api/tasks/create' target='#task-list' swap='beforeend'|>
    <input name="title" />
</form>

<!-- PUT request -->
<button |htmx-put '/api/tasks/1' target='#task-1' swap='outerHTML'|>
    Save Task
</button>

<!-- DELETE request -->
<button |htmx-delete '/api/tasks/1' target='#task-1' swap='delete'|>
    Delete Task
</button>

<!-- PATCH request -->
<button |htmx-patch '/api/tasks/1/status' target='#task-1' swap='outerHTML'|>
    Toggle Status
</button>
```

---

### 32. Alpine.js Integration & Reactive State Tags
Abstract Alpine.js core script tags, plugin CDN references, `x-cloak` CSS rules, and reactive component state declarations into single inline tags.

#### A. Alpine.js Head Setup Tag (`|alpine ...|`)
```html
<head>
    <!-- Loads Alpine.js core, collapse & focus plugins, and x-cloak CSS rules -->
    |alpine plugins='collapse,focus' cloak=true|
</head>
```

#### B. Alpine.js Reactive Component State Tag (`|alpine-data ...|`)
Use `|alpine-data ...|` to declare `x-data` reactive state:
```html
<div |alpine-data open=false count=0 tab='home'|>
    <button @click="open = !open">Toggle Menu</button>
    <div |alpine-show 'open'| |alpine-cloak|>
        <p>Active Tab: <span x-text="tab"></span></p>
    </div>
</div>
```

#### C. Other Alpine Directives
Write any Alpine.js directive attribute shorthand using `|alpine-<directive> ...|`:
```html
<!-- x-show shorthand -->
<div |alpine-show "open"|>Visible if open</div>

<!-- x-text shorthand -->
<span |alpine-text "username"|></span>

<!-- x-html shorthand -->
<div |alpine-html "renderedMarkdown"|></div>

<!-- x-model shorthand -->
<input |alpine-model "searchQuery"| />

<!-- x-transition shorthand -->
<div |alpine-show "open"| |alpine-transition|>Fades in/out</div>

<!-- x-cloak shorthand -->
<div |alpine-cloak|>Hidden until Alpine compiles</div>

<!-- x-for shorthand -->
<template |alpine-for "item in items"|>
    <li x-text="item"></li>
</template>

<!-- x-if shorthand -->
<template |alpine-if "open"|>
    <div>Conditional content</div>
</template>

<!-- x-effect shorthand -->
<div |alpine-effect "console.log(count)"|></div>

<!-- x-ref shorthand -->
<button |alpine-ref "submitButton"|>Submit</button>

<!-- x-ignore shorthand -->
<div |alpine-ignore|>Ignored by Alpine</div>

<!-- x-init shorthand -->
<div |alpine-init "console.log('Initialized')"|></div>
```

---

### 33. Compiler Syntax Strictness, Route Security & CSP Compatibility

#### A. Directive Identifier Rules & Strict Validation
Directives with empty or malformed identifier names (`|macro ()|`, `|fragment |`, `|section |`, `|slot |`, `|include |`, `|layout |`, `|component |`) or invalid path syntax (`|include /abs/path|`, `|include ../traversal|`) throw `TemplateSyntaxException` at compile time.

Identifiers must match regex `[A-Za-z_][A-Za-z0-9_-]*`.

#### B. Unknown Directives & Fuzzy Spelling Suggestions
Unknown directives or typos (`|inculde|`, `|ifx|`, `|wat nonsense|`) trigger compile-time errors with fuzzy spelling suggestions:
```text
TemplateSyntaxException: Unknown directive '|inculde partials/header|' at line 6, column 9. Did you mean '|include|'?
```

#### C. Directive Attribute Parsing
Directives (`|pwa ...|`, `|htmx ...|`, `|alpine ...|`) use a unified attribute tokenizer. Attributes support single/double quotes, unquoted booleans/numbers, escaped quotes, spaces inside quotes, and optional `=` whitespace:
```pte
|pwa name='Task Master' theme="#123456" sw='/sw.js'|
```
Duplicate attribute declarations (`name='A' name='B'`) or unclosed quotes are rejected at compile time.

#### D. Route & Query Model Namespaces and Precedence
In file-based routes, URL path variables and query parameters are exposed both in root model and under explicit `route` and `query` namespaces (`|route.id|`, `|query.id|`). Path variables take precedence over query parameters on key collisions.

#### E. Safe Production HTTP Error Handling
Errors during file-route rendering return a generic `"Internal Server Error"` response in production, logging full stack traces with an `X-Correlation-ID` response header. HTTP 401 Unauthorized and 403 Forbidden statuses are preserved.

#### F. Fail-Fast Startup Route Discovery
Malformed template route files, duplicate route patterns, or unclosed dynamic path brackets (`[id`) trigger `RouteDiscoveryException` at application startup when `spring.pipedtemplate.routing.fail-fast=true` (default).

#### G. Strict Content Security Policy (CSP) PWA Compatibility
PWA registration supports external script mode (`|pwa sw='/sw.js' registration-script='/pte-assets/pwa-register.js'|`) and nonce attributes (`|pwa sw='/sw.js' nonce='rAnd0mN0nc3'|`) to comply with strict CSP policies (`default-src 'self'; script-src 'self'`).