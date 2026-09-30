const APP_NAME = "operationstool";
const APP_URL = "/" + APP_NAME;
const URLBACKEND = APP_URL + "/";

function loadOperationsChrome() {
    $("#section_sidebar").load(APP_URL + "/components/menu.html", function () {
        if (window.initSidebarBehavior) window.initSidebarBehavior();
        if (window.onOperationsMenuLoaded) window.onOperationsMenuLoaded();
    });
    $("#section_header").load(APP_URL + "/components/header.html");
    $("#section_footer").load(APP_URL + "/components/footer.html");
}

window.initSidebarBehavior = function (attempt) {
    attempt = attempt || 0;
    const storageKey = APP_NAME + "_sidebar_state";
    const legacyKey = APP_NAME + "sidebar-mini";
    const sidebar = document.getElementById("sidebar");
    const content = document.getElementById("content");

    if (!sidebar || !content) {
        if (attempt < 20) {
            setTimeout(function () { window.initSidebarBehavior(attempt + 1); }, 200);
        }
        return;
    }
    if (sidebar.dataset.sidebarReady === "1") return;
    sidebar.dataset.sidebarReady = "1";

    function readExpanded() {
        const stored = localStorage.getItem(storageKey);
        if (stored === "collapsed") return false;
        if (stored === "expanded") return true;
        return localStorage.getItem(legacyKey) !== "true";
    }

    function applyState(expanded) {
        document.body.classList.toggle("sidebar-mini", !expanded);
        sidebar.classList.toggle("expanded", expanded);
        sidebar.classList.toggle("collapsed", !expanded);
        content.classList.toggle("with-rail", !expanded);
        localStorage.setItem(storageKey, expanded ? "expanded" : "collapsed");
    }

    applyState(readExpanded());

    window.toggleSidebar = function () {
        applyState(!sidebar.classList.contains("expanded"));
    };

    const toggle = sidebar.querySelector("#btnToggleSidebar");
    if (toggle) {
        toggle.onclick = function (event) {
            event.preventDefault();
            window.toggleSidebar();
        };
    }
};
