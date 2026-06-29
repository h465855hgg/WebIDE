/*
 * WebIDE - A powerful IDE for Android web development.
 * Copyright (C) 2025  如日中天  <3382198490@qq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.web.webide.lsp

/**
 * 各语言关键字与内置标识符定义（纯离线，不依赖任何外部安装）
 */
object LanguageKeywords {

    // ==================== JavaScript / TypeScript ====================
    val JS_KEYWORDS = arrayOf(
        // 关键字
        "var", "let", "const", "function", "return", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "new", "delete", "typeof", "instanceof",
        "in", "of", "this", "super", "class", "extends", "static", "get", "set", "async",
        "await", "yield", "import", "export", "default", "from", "as", "try", "catch",
        "finally", "throw", "void", "with", "debugger",
        // 字面量
        "true", "false", "null", "undefined", "NaN", "Infinity",
        // 内置全局对象
        "console", "window", "document", "globalThis", "global", "process", "Buffer",
        "module", "exports", "require", "__dirname", "__filename",
        // 内置对象类型
        "Object", "Array", "String", "Number", "Boolean", "RegExp", "Date", "Math",
        "JSON", "Promise", "Map", "Set", "WeakMap", "WeakSet", "Symbol", "Proxy",
        "Reflect", "Error", "TypeError", "RangeError", "SyntaxError", "Function",
        "Intl", "ArrayBuffer", "DataView", "Float32Array", "Float64Array",
        "Int8Array", "Int16Array", "Int32Array", "Uint8Array", "Uint16Array", "Uint32Array",
        // 常用全局函数
        "parseInt", "parseFloat", "isNaN", "isFinite", "encodeURI", "decodeURI",
        "encodeURIComponent", "decodeURIComponent", "eval", "setTimeout", "setInterval",
        "clearTimeout", "clearInterval", "setImmediate", "queueMicrotask",
        "requestAnimationFrame", "cancelAnimationFrame",
        // console 方法
        "log", "error", "warn", "info", "debug", "trace", "table", "group", "groupEnd",
        "time", "timeEnd", "assert", "count", "dir",
        // Array 方法
        "push", "pop", "shift", "unshift", "slice", "splice", "concat", "join",
        "reverse", "sort", "indexOf", "lastIndexOf", "find", "findIndex", "filter",
        "map", "reduce", "reduceRight", "forEach", "some", "every", "includes",
        "fill", "flat", "flatMap", "at", "entries", "keys", "values", "from", "of", "isArray",
        // Object 方法
        "assign", "create", "freeze", "seal", "defineProperty", "defineProperties",
        "getPrototypeOf", "setPrototypeOf", "getOwnPropertyDescriptor", "keys", "values",
        "entries", "fromEntries", "is", "isFrozen", "isSealed", "isExtensible",
        // String 方法
        "charAt", "charCodeAt", "codePointAt", "startsWith", "endsWith", "includes",
        "indexOf", "lastIndexOf", "match", "matchAll", "replace", "replaceAll",
        "search", "split", "substring", "substr", "toLowerCase", "toUpperCase",
        "trim", "trimStart", "trimEnd", "padStart", "padEnd", "repeat", "normalize",
        "at", "concat", "localeCompare",
        // Math 方法
        "abs", "ceil", "floor", "round", "max", "min", "pow", "sqrt", "cbrt",
        "random", "sign", "trunc", "exp", "log", "sin", "cos", "tan", "atan", "atan2",
        // Promise 方法
        "resolve", "reject", "all", "allSettled", "race", "any",
        // JSON 方法
        "stringify", "parse",
        // DOM 常用属性/方法
        "getElementById", "getElementsByClassName", "getElementsByTagName",
        "querySelector", "querySelectorAll", "createElement", "createTextNode",
        "appendChild", "removeChild", "insertBefore", "replaceChild", "cloneNode",
        "addEventListener", "removeEventListener", "dispatchEvent",
        "innerHTML", "innerText", "textContent", "className", "classList",
        "style", "value", "checked", "disabled", "href", "src", "alt", "title",
        "type", "name", "id", "placeholder", "required", "readonly", "hidden",
        "width", "height", "parentNode", "parentElement", "childNodes", "children",
        "firstChild", "lastChild", "firstElementChild", "lastElementChild",
        "nextSibling", "previousSibling", "nextElementSibling", "previousElementSibling",
        "setAttribute", "getAttribute", "removeAttribute", "hasAttribute",
        "focus", "blur", "click", "submit", "reset", "preventDefault", "stopPropagation",
        // fetch / XHR
        "fetch", "then", "catch", "finally", "Response", "Request", "Headers",
        "XMLHttpRequest", "open", "send", "setRequestHeader", "onload", "onerror",
        "onreadystatechange", "readyState", "status", "statusText", "responseText",
        "responseType", "onprogress", "abort",
        // 事件类型
        "onclick", "onload", "onsubmit", "onchange", "oninput", "onfocus", "onblur",
        "onmouseover", "onmouseout", "onmousedown", "onmouseup", "onmousemove",
        "onkeydown", "onkeyup", "onkeypress", "onresize", "onscroll", "oncontextmenu",
        // Web API
        "localStorage", "sessionStorage", "cookie", "history", "location", "navigator",
        "screen", "alert", "confirm", "prompt", "open", "close", "print",
        "URL", "URLSearchParams", "FormData", "Blob", "File", "FileReader",
        "WebSocket", "Worker", "SharedWorker", "MessageChannel",
        "requestAnimationFrame", "CanvasRenderingContext2D",
        "getContext", "fillRect", "strokeRect", "clearRect", "beginPath",
        "moveTo", "lineTo", "arc", "fill", "stroke", "closePath", "save", "restore",
        "translate", "rotate", "scale", "drawImage", "fillText", "strokeText",
        "createLinearGradient", "createRadialGradient", "addColorStop"
    )

    // ==================== HTML ====================
    val HTML_KEYWORDS = arrayOf(
        // 标签
        "html", "head", "body", "title", "meta", "link", "style", "script", "base",
        "div", "span", "p", "br", "hr", "pre", "blockquote", "address",
        "h1", "h2", "h3", "h4", "h5", "h6",
        "ul", "ol", "li", "dl", "dt", "dd",
        "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "col", "colgroup",
        "form", "input", "button", "select", "option", "optgroup", "textarea", "label",
        "fieldset", "legend", "datalist", "output", "progress", "meter",
        "a", "img", "area", "map", "picture", "source", "audio", "video", "track",
        "iframe", "embed", "object", "param", "canvas", "svg",
        "header", "footer", "nav", "main", "section", "article", "aside",
        "figure", "figcaption", "details", "summary", "dialog", "mark", "time",
        "code", "pre", "kbd", "samp", "var", "sub", "sup", "small", "strong", "em",
        "b", "i", "u", "s", "del", "ins", "abbr", "cite", "q", "dfn", "ruby", "rt", "rp",
        "bdi", "bdo", "wbr",
        // 常用属性
        "class", "id", "style", "href", "src", "alt", "title", "type", "name", "value",
        "placeholder", "required", "disabled", "readonly", "checked", "selected",
        "onclick", "onload", "onsubmit", "onchange", "oninput", "onfocus", "onblur",
        "onmouseover", "onmouseout", "onkeydown", "onkeyup", "onkeypress",
        "onmousedown", "onmouseup", "onmousemove", "onresize", "onscroll",
        "action", "method", "enctype", "target", "rel", "media", "sizes", "srcset",
        "width", "height", "cols", "rows", "maxlength", "minlength", "min", "max",
        "step", "pattern", "autocomplete", "autofocus", "multiple", "size", "wrap",
        "colspan", "rowspan", "headers", "scope", "span", "align", "valign",
        "bgcolor", "color", "background", "border", "cellpadding", "cellspacing",
        "role", "tabindex", "contenteditable", "draggable", "hidden", "spellcheck",
        "dir", "lang", "translate", "accesskey", "contextmenu",
        "data-id", "data-value", "data-type", "data-src", "data-target", "data-toggle",
        "aria-label", "aria-hidden", "aria-describedby", "aria-labelledby",
        "aria-expanded", "aria-selected", "aria-checked", "aria-disabled",
        "aria-controls", "aria-owns", "aria-live", "aria-relevant",
        // meta 属性
        "charset", "content", "http-equiv", "property", "viewport", "description",
        "keywords", "author", "robots", "refresh", "og:title", "og:description", "og:image",
        // input type 值
        "text", "password", "email", "number", "tel", "url", "search", "date",
        "datetime-local", "time", "month", "week", "color", "range", "file",
        "checkbox", "radio", "submit", "reset", "button", "image", "hidden",
        // link rel 值
        "stylesheet", "icon", "shortcut icon", "apple-touch-icon", "manifest",
        "preload", "prefetch", "preconnect", "dns-prefetch", "canonical",
        // DOCTYPE
        "DOCTYPE", "html", "public"
    )

    // ==================== CSS ====================
    val CSS_KEYWORDS = arrayOf(
        // 属性
        "color", "background", "background-color", "background-image", "background-repeat",
        "background-position", "background-size", "background-attachment", "background-clip",
        "background-origin", "background-blend-mode",
        "border", "border-top", "border-right", "border-bottom", "border-left",
        "border-color", "border-width", "border-style", "border-radius",
        "border-top-left-radius", "border-top-right-radius", "border-bottom-left-radius",
        "border-bottom-right-radius", "border-collapse", "border-spacing", "border-image",
        "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
        "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
        "width", "height", "min-width", "min-height", "max-width", "max-height",
        "display", "position", "top", "right", "bottom", "left", "z-index",
        "float", "clear", "overflow", "overflow-x", "overflow-y", "visibility",
        "opacity", "clip", "resize", "object-fit", "object-position", "vertical-align",
        "font", "font-family", "font-size", "font-weight", "font-style", "font-variant",
        "font-stretch", "line-height", "letter-spacing", "word-spacing", "word-break",
        "word-wrap", "overflow-wrap", "white-space", "text-align", "text-decoration",
        "text-transform", "text-indent", "text-shadow", "text-overflow", "text-rendering",
        "direction", "unicode-bidi", "writing-mode", "tab-size", "hyphens",
        "list-style", "list-style-type", "list-style-position", "list-style-image",
        "table-layout", "caption-side", "empty-cells",
        "flex", "flex-direction", "flex-wrap", "flex-flow", "justify-content",
        "align-items", "align-self", "align-content", "flex-grow", "flex-shrink",
        "flex-basis", "order", "gap", "row-gap", "column-gap",
        "grid", "grid-template-columns", "grid-template-rows", "grid-template-areas",
        "grid-area", "grid-column", "grid-row", "grid-column-start", "grid-column-end",
        "grid-row-start", "grid-row-end", "grid-auto-flow", "grid-auto-columns",
        "grid-auto-rows", "grid-gap", "justify-items", "justify-self", "place-items",
        "place-content", "place-self",
        "box-shadow", "box-sizing", "box-decoration-break",
        "transform", "transform-origin", "transform-style",
        "transition", "transition-property", "transition-duration",
        "transition-timing-function", "transition-delay",
        "animation", "animation-name", "animation-duration", "animation-timing-function",
        "animation-delay", "animation-iteration-count", "animation-direction",
        "animation-fill-mode", "animation-play-state",
        "filter", "backdrop-filter", "will-change", "cursor", "user-select",
        "pointer-events", "outline", "outline-style", "outline-color", "outline-width",
        "outline-offset", "appearance", "contain", "content", "counter-reset",
        "counter-increment", "quotes", "all",
        // 值
        "auto", "none", "inherit", "initial", "unset", "revert", "currentColor",
        "transparent", "solid", "dotted", "dashed", "double", "groove", "ridge",
        "inset", "outset", "hidden", "visible", "scroll", "clip",
        "block", "inline", "inline-block", "inline-flex", "inline-grid", "inline-table",
        "flex", "grid", "table", "table-cell", "table-row", "table-column",
        "list-item", "run-in", "contents", "flow-root",
        "static", "relative", "absolute", "fixed", "sticky",
        "left", "right", "center", "top", "bottom", "justify", "space-between",
        "space-around", "space-evenly", "stretch", "normal", "baseline", "start", "end",
        "wrap", "nowrap", "wrap-reverse", "row", "row-reverse", "column", "column-reverse",
        "bold", "normal", "italic", "oblique", "lighter", "bolder", "smaller", "larger",
        "underline", "overline", "line-through", "blink", "uppercase", "lowercase",
        "capitalize", "pre", "pre-wrap", "pre-line", "nowrap",
        "pointer", "default", "crosshair", "text", "wait", "help", "move",
        "n-resize", "s-resize", "e-resize", "w-resize", "ne-resize", "nw-resize",
        "se-resize", "sw-resize", "grab", "grabbing", "not-allowed", "zoom-in", "zoom-out",
        "ease", "ease-in", "ease-out", "ease-in-out", "linear", "cubic-bezier", "step-start",
        "step-end", "steps", "infinite", "alternate", "alternate-reverse", "forwards",
        "backwards", "both", "running", "paused", "forwards",
        "contain", "cover", "fill", "scale-down", "none",
        "rotate", "rotateX", "rotateY", "rotateZ", "scale", "scaleX", "scaleY", "scaleZ",
        "skew", "skewX", "skewY", "translate", "translateX", "translateY", "translateZ",
        "translate3d", "matrix", "matrix3d", "perspective",
        "blur", "brightness", "contrast", "drop-shadow", "grayscale", "hue-rotate",
        "invert", "opacity", "saturate", "sepia",
        "rgb", "rgba", "hsl", "hsla", "hex", "url", "var", "calc", "attr",
        "min-content", "max-content", "fit-content", "repeat", "minmax", "auto-fill",
        "auto-fit", "span", "dense", "column", "row",
        // 单位
        "px", "em", "rem", "vh", "vw", "vmin", "vmax", "%", "pt", "pc", "in", "cm", "mm",
        "ex", "ch", "fr", "deg", "rad", "turn", "s", "ms", "Hz", "kHz",
        // @ 规则
        "@media", "@keyframes", "@import", "@font-face", "@supports", "@page",
        "@charset", "@namespace", "@document", "@viewport", "@counter-style",
        "@layer", "@container", "@scope", "@starting-style",
        // 伪类/伪元素
        ":hover", ":active", ":focus", ":visited", ":link", ":first-child", ":last-child",
        ":only-child", ":nth-child", ":nth-of-type", ":first-of-type", ":last-of-type",
        ":only-of-type", ":empty", ":not", ":checked", ":disabled", ":enabled",
        ":required", ":optional", ":valid", ":invalid", ":target", ":root", ":lang",
        ":before", ":after", "::before", "::after", "::first-letter", "::first-line",
        "::selection", "::placeholder", "::marker", "::backdrop", "::file-selector-button",
        // 媒体查询
        "screen", "print", "all", "speech", "max-width", "min-width", "max-height",
        "min-height", "orientation", "portrait", "landscape", "resolution",
        "prefers-color-scheme", "prefers-reduced-motion", "prefers-contrast",
        "dark", "light", "no-preference", "reduce", "no-reduce", "more", "less"
    )

    // ==================== JSON ====================
    val JSON_KEYWORDS = arrayOf(
        "true", "false", "null"
    )

    // ==================== PHP ====================
    val PHP_KEYWORDS = arrayOf(
        "echo", "print", "var", "let", "const", "static", "public", "private", "protected",
        "function", "return", "if", "else", "elseif", "endif", "for", "foreach", "while",
        "do", "switch", "case", "break", "continue", "new", "class", "extends", "implements",
        "interface", "abstract", "final", "namespace", "use", "trait", "try", "catch",
        "finally", "throw", "instanceof", "clone", "yield", "async", "await",
        "true", "false", "null", "array", "object", "string", "int", "float", "bool",
        "void", "mixed", "callable", "iterable", "self", "parent", "this",
        "isset", "empty", "unset", "define", "defined", "gettype", "settype",
        "is_array", "is_string", "is_int", "is_float", "is_bool", "is_null",
        "is_object", "is_callable", "is_numeric", "is_scalar",
        "count", "strlen", "strpos", "str_replace", "str_repeat", "strtolower", "strtoupper",
        "trim", "ltrim", "rtrim", "explode", "implode", "substr", "sprintf", "printf",
        "json_encode", "json_decode", "serialize", "unserialize",
        "array_push", "array_pop", "array_shift", "array_unshift", "array_merge",
        "array_map", "array_filter", "array_reduce", "array_keys", "array_values",
        "array_search", "in_array", "array_key_exists", "sort", "rsort", "asort", "arsort",
        "header", "session_start", "session_destroy", "session_id", "session_unset",
        "setcookie", "\$_GET", "\$_POST", "\$_SESSION", "\$_COOKIE", "\$_SERVER", "\$_REQUEST",
        "\$_FILES", "\$_ENV", "\$GLOBALS", "mysqli_connect", "mysqli_query", "mysqli_fetch_assoc",
        "PDO", "prepare", "execute", "fetch", "fetchAll", "bindParam", "bindValue",
        "file_get_contents", "file_put_contents", "fopen", "fclose", "fread", "fwrite",
        "fgets", "file_exists", "is_dir", "is_file", "mkdir", "rmdir", "unlink",
        "scandir", "glob", "dirname", "basename", "realpath", "include", "require",
        "include_once", "require_once", "exit", "die", "date", "time", "mktime",
        "strtotime", "date_default_timezone_set", "microtime",
        "preg_match", "preg_replace", "preg_match_all", "preg_split",
        "curl_init", "curl_setopt", "curl_exec", "curl_close",
        "filter_var", "filter_input", "htmlspecialchars", "htmlentities",
        "nl2br", "strip_tags", "wordwrap", "number_format", "round", "ceil", "floor",
        "abs", "max", "min", "rand", "mt_rand", "sqrt", "pow", "log", "exp"
    )

    // ==================== C / C++ ====================
    val C_KEYWORDS = arrayOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
        "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
        "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
        "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
        "_Bool", "_Complex", "_Imaginary", "_Alignas", "_Alignof", "_Atomic",
        "_Generic", "_Noreturn", "_Static_assert", "_Thread_local",
        "printf", "scanf", "fprintf", "fscanf", "sprintf", "sscanf", "snprintf",
        "puts", "putchar", "getchar", "gets", "fgets", "fputs", "fputc", "fgetc",
        "fopen", "fclose", "fread", "fwrite", "fseek", "ftell", "rewind", "feof", "fflush",
        "malloc", "calloc", "realloc", "free", "exit", "abort", "atexit",
        "memcpy", "memmove", "memset", "memcmp", "strcpy", "strncpy", "strcat", "strncat",
        "strcmp", "strncmp", "strlen", "strchr", "strrchr", "strstr", "strtok",
        "atoi", "atol", "atof", "strtol", "strtod", "rand", "srand",
        "abs", "labs", "fabs", "sqrt", "pow", "exp", "log", "sin", "cos", "tan",
        "ceil", "floor", "round", "fmod", "isalpha", "isdigit", "isalnum", "isspace",
        "toupper", "tolower", "assert", "errno", "NULL", "EOF", "FILE", "stdin", "stdout", "stderr"
    )

    val CPP_KEYWORDS = arrayOf(
        "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor",
        "bool", "break", "case", "catch", "char", "char8_t", "char16_t", "char32_t",
        "class", "compl", "concept", "const", "consteval", "constexpr", "constinit",
        "const_cast", "continue", "co_await", "co_return", "co_yield", "decltype",
        "default", "delete", "do", "double", "dynamic_cast", "else", "enum", "explicit",
        "export", "extern", "false", "float", "for", "friend", "goto", "if", "inline",
        "int", "long", "mutable", "namespace", "new", "noexcept", "not", "not_eq",
        "nullptr", "operator", "or", "or_eq", "private", "protected", "public",
        "register", "reinterpret_cast", "requires", "return", "short", "signed",
        "sizeof", "static", "static_assert", "static_cast", "struct", "switch",
        "template", "this", "thread_local", "throw", "true", "try", "typedef", "typeid",
        "typename", "union", "unsigned", "using", "virtual", "void", "volatile",
        "wchar_t", "while", "xor", "xor_eq",
        "std", "cout", "cin", "cerr", "clog", "endl", "string", "vector", "map",
        "unordered_map", "set", "unordered_set", "list", "deque", "queue", "stack",
        "pair", "tuple", "array", "forward_list", "shared_ptr", "unique_ptr",
        "weak_ptr", "make_shared", "make_unique", "make_pair", "make_tuple",
        "push_back", "push_front", "pop_back", "pop_front", "size", "empty",
        "clear", "begin", "end", "find", "insert", "erase", "at", "front", "back",
        "reserve", "resize", "capacity", "data", "c_str", "length", "substr",
        "append", "replace", "find_first_of", "find_last_of", "rfind", "compare",
        "sort", "reverse", "max", "min", "abs", "swap", "count", "fill", "copy",
        "move", "transform", "accumulate", "lower_bound", "upper_bound",
        "binary_search", "unique", "remove", "remove_if", "function", "bind",
        "thread", "mutex", "lock_guard", "unique_lock", "async", "future", "promise",
        "iostream", "fstream", "sstream", "sstream", "iomanip", "cmath", "cstdlib",
        "cstring", "vector", "algorithm", "memory", "utility", "functional",
        "map", "set", "queue", "stack", "deque", "list", "string", "chrono",
        "atomic", "condition_variable", "thread", "mutex", "future", "ratio"
    )

    // ==================== GLSL ====================
    val GLSL_KEYWORDS = arrayOf(
        "attribute", "const", "uniform", "varying", "in", "out", "inout",
        "centroid", "flat", "smooth", "noperspective", "patch", "sample",
        "break", "continue", "do", "for", "while", "switch", "case", "default",
        "if", "else", "discard", "return",
        "subroutine", "invariant", "precise", "highp", "mediump", "lowp",
        "precision", "struct",
        "void", "bool", "int", "uint", "float", "double",
        "vec2", "vec3", "vec4", "dvec2", "dvec3", "dvec4",
        "ivec2", "ivec3", "ivec4", "uvec2", "uvec3", "uvec4", "bvec2", "bvec3", "bvec4",
        "mat2", "mat3", "mat4", "mat2x3", "mat2x4", "mat3x2", "mat3x4", "mat4x2", "mat4x3",
        "dmat2", "dmat3", "dmat4", "dmat2x3", "dmat2x4", "dmat3x2", "dmat3x4", "dmat4x2", "dmat4x3",
        "sampler1D", "sampler2D", "sampler3D", "samplerCube",
        "sampler1DArray", "sampler2DArray", "samplerCubeArray",
        "sampler1DShadow", "sampler2DShadow", "samplerCubeShadow",
        "sampler2DMS", "sampler2DMSArray",
        "isampler1D", "isampler2D", "isampler3D", "isamplerCube",
        "usampler1D", "usampler2D", "usampler3D", "usamplerCube",
        "gl_Position", "gl_FragColor", "gl_FragCoord", "gl_PointSize",
        "gl_PointCoord", "gl_FragDepth", "gl_VertexID", "gl_InstanceID",
        "gl_DrawID", "gl_BaseVertex", "gl_BaseInstance",
        "texture", "texture2D", "texture3D", "textureCube", "textureLod",
        "textureProj", "textureGrad", "textureOffset", "texelFetch",
        "mix", "clamp", "smoothstep", "step", "length", "distance", "dot", "cross",
        "normalize", "reflect", "refract", "faceforward", "pow", "exp", "log",
        "exp2", "log2", "sqrt", "inversesqrt", "abs", "sign", "floor", "ceil",
        "fract", "mod", "min", "max", "radians", "degrees", "sin", "cos", "tan",
        "asin", "acos", "atan", "transpose", "inverse", "determinant",
        "matrixCompMult", "outerProduct", "lessThan", "greaterThan", "equal",
        "all", "any", "true", "false"
    )

    /**
     * 根据文件扩展名获取关键字数组
     */
    fun getKeywords(extension: String): Array<String> {
        return when (extension.lowercase()) {
            "js", "javascript", "ts", "typescript", "tsx", "jsx", "mjs", "cjs" -> JS_KEYWORDS
            "html", "htm", "xhtml" -> HTML_KEYWORDS
            "css", "scss", "sass", "less" -> CSS_KEYWORDS
            "json" -> JSON_KEYWORDS
            "php", "phtml" -> PHP_KEYWORDS
            "c", "h" -> C_KEYWORDS
            "cpp", "hpp", "cc", "cxx", "hxx" -> CPP_KEYWORDS
            "glsl", "vert", "frag", "shader" -> GLSL_KEYWORDS
            else -> emptyArray()
        }
    }
}
