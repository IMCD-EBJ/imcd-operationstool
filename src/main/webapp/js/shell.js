const APP_NAME = "operationstool";
const APP_URL = "/" + APP_NAME;
const URLBACKEND = APP_URL + "/";

function readStoredJson(key) {
    try {
        return JSON.parse(localStorage.getItem(key) || "null");
    } catch (e) {
        return null;
    }
}

function sessionFromCommonApps(commonUser) {
    if (!commonUser || typeof commonUser !== "object") {
        return null;
    }
    const localAd = commonUser.UCA_LocalADUser || commonUser.UCA_Id || "";
    const userName = commonUser.UCA_UserName || localAd;
    const mail = commonUser.UCA_Mail || commonUser.UCA_User || "";
    if (!localAd && !userName && !mail) {
        return null;
    }
    const profiles = [];
    if (Array.isArray(commonUser.Profiles)) {
        commonUser.Profiles.forEach(function (profile) {
            if (profile && profiles.indexOf(profile) < 0) {
                profiles.push(profile);
            }
        });
    }
    if (commonUser.UCA_TipoUserId && profiles.indexOf(commonUser.UCA_TipoUserId) < 0) {
        profiles.push(commonUser.UCA_TipoUserId);
    }
    return {
        CFG_SendEmail: false,
        UOT_Id: userName || localAd,
        UOT_UserName: userName || localAd,
        UOT_LocalADUser: localAd || userName,
        UOT_Mail: mail,
        Profiles: profiles
    };
}

function ensureOperationsSession() {
    const existing = readStoredJson(APP_NAME + "usuario");
    if (existing) {
        return existing;
    }
    const session = sessionFromCommonApps(readStoredJson("commonAppsusuario"));
    if (!session) {
        return null;
    }
    localStorage.setItem(APP_NAME + "usuario", JSON.stringify(session));
    if (!localStorage.getItem(APP_NAME + "usuarioOriginal")) {
        localStorage.setItem(APP_NAME + "usuarioOriginal", JSON.stringify(session));
    }
    return session;
}

ensureOperationsSession();

function loadOperationsChrome() {
    $("#section_sidebar").load(APP_URL + "/components/menu.html", function () {
        if (window.initSidebarBehavior) window.initSidebarBehavior();
        initOperationsMenu();
    });
    $("#section_header").load(APP_URL + "/components/header.html");
    $("#section_footer").load(APP_URL + "/components/footer.html");
}

function operationsMenuUserId() {
    const session = ensureOperationsSession();
    return session && session.UOT_LocalADUser ? session.UOT_LocalADUser : "";
}

async function initOperationsMenu() {
    const menu = document.getElementById("menu");
    if (!menu) return;

    try {
        const response = await fetch(
            URLBACKEND + "utils/getMenu?UOT_Id=" + encodeURIComponent(operationsMenuUserId())
        );
        if (!response.ok) {
            throw new Error("Menu request failed");
        }
        let data = await response.json();
        data = (data || []).sort(function (a, b) {
            return a.OrderMenu - b.OrderMenu;
        });
        menu.replaceChildren();
        data.forEach(function (menuItem) {
            if (menuItem.ParentId == null || menuItem.ParentId === "") {
                menu.appendChild(createMenuElement(menuItem, data));
            }
        });
    } catch (error) {
        console.error("Error initializing menu:", error);
    }

    if (window.onOperationsMenuLoaded) window.onOperationsMenuLoaded();
}

function createMenuElement(menuItem, allMenus) {
    const li = document.createElement("li");
    const a = document.createElement("a");
    const childMenus = allMenus
        .filter(function (menu) {
            return menu.ParentId != null && String(menu.ParentId) === String(menuItem.Id);
        })
        .sort(function (left, right) {
            return left.OrderMenu - right.OrderMenu;
        });
    const currentPage = window.location.pathname.split("/").pop();

    if (menuItem.Url) {
        a.setAttribute("href", menuItem.Url);
        if (menuItem.Url === currentPage) {
            a.classList.add("active");
        }
    } else {
        a.setAttribute("href", "#homeSubmenu" + menuItem.Id);
        a.setAttribute("data-toggle", "collapse");
        a.setAttribute("aria-expanded", "true");
        a.classList.add("dropdown-toggle");
    }
    a.classList.add("level1-link");

    if (menuItem.Icon) {
        const icon = document.createElement("span");
        icon.className = "icon";
        const glyph = document.createElement("i");
        glyph.className = menuItem.Icon;
        icon.appendChild(glyph);
        a.appendChild(icon);
    }

    const label = document.createElement("span");
    label.className = "label";
    label.textContent = menuItem.Name || "";
    a.appendChild(label);
    li.appendChild(a);

    if (childMenus.length > 0) {
        const ul = document.createElement("ul");
        ul.className = "collapse list-unstyled show";
        ul.id = "homeSubmenu" + menuItem.Id;
        childMenus.forEach(function (childMenu) {
            ul.appendChild(createMenuElement(childMenu, allMenus));
        });
        li.appendChild(ul);
    }

    return li;
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
