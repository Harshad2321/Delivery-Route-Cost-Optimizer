(function () {
  var API_BASE = "http://localhost:8081";
  var FALLBACK_USERS_KEY = "drc_fallback_accounts_v1";
  var SESSION_KEY = "drc_active_session_v1";
  var DELIVERY_ORDER_CACHE = {};
  var DELIVERY_SELECTED_ORDER_ID = null;
  var ORDER_LIFECYCLE_FILTER = "ALL";
  var DELIVERY_CITY_COORDS = {
    Mumbai: { lat: 19.076, lng: 72.8777 },
    Pune: { lat: 18.5204, lng: 73.8567 },
    Aurangabad: { lat: 19.8762, lng: 75.3433 },
    Nashik: { lat: 19.9975, lng: 73.7898 },
    Nagpur: { lat: 21.1458, lng: 79.0882 },
    Ahmedabad: { lat: 23.0225, lng: 72.5714 },
    Indore: { lat: 22.7196, lng: 75.8577 },
    Bhopal: { lat: 23.1815, lng: 79.9864 }
  };

  function getDeliveryCityCoordinates(cityName) {
    return DELIVERY_CITY_COORDS[cityName] || null;
  }

  function estimateDeliveryFallbackDistance(fromCity, toCity) {
    if (!fromCity || !toCity) {
      return 0;
    }

    var earthRadiusKm = 6371;
    var toRadians = function (value) {
      return value * Math.PI / 180;
    };
    var dLat = toRadians(toCity.lat - fromCity.lat);
    var dLng = toRadians(toCity.lng - fromCity.lng);
    var lat1 = toRadians(fromCity.lat);
    var lat2 = toRadians(toCity.lat);
    var a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
      + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return Math.round((earthRadiusKm * c) * 1.26);
  }

  function byId(id) {
    return document.getElementById(id);
  }

  function show(el) {
    if (el) {
      el.classList.remove("hidden");
    }
  }

  function hide(el) {
    if (el) {
      el.classList.add("hidden");
    }
  }

  function setText(id, text) {
    var el = byId(id);
    if (el) {
      el.textContent = text;
    }
  }

  function parseServerTimestamp(value) {
    if (!value) {
      return 0;
    }

    var normalized = String(value).trim().replace(" ", "T");
    var parsed = Date.parse(normalized);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function sortOrdersByNewestFirst(orders) {
    return orders.slice().sort(function (left, right) {
      return parseServerTimestamp(right.placedAt) - parseServerTimestamp(left.placedAt) || right.id - left.id;
    });
  }

  function normalizeLifecycleFilter(filterName) {
    return String(filterName || "ALL").toUpperCase();
  }

  function matchesLifecycleFilter(order, filterName) {
    var normalized = normalizeLifecycleFilter(filterName);
    var status = String(order.status || "").toUpperCase();

    if (normalized === "CREATED") {
      return status === "CREATED" || status === "PLACED";
    }
    if (normalized === "ACCEPTED") {
      return status === "ACCEPTED";
    }
    if (normalized === "ON_THE_WAY") {
      return status === "PICKED" || status === "IN_TRANSIT";
    }
    if (normalized === "DELIVERED") {
      return status === "DELIVERED";
    }
    return true;
  }

  function updateLifecycleButtonState(activeFilter) {
    var buttonMap = {
      ordersCreatedTab: "CREATED",
      ordersAcceptedTab: "ACCEPTED",
      ordersOnWayTab: "ON_THE_WAY",
      ordersDeliveredTab: "DELIVERED"
    };

    Object.keys(buttonMap).forEach(function (buttonId) {
      var button = byId(buttonId);
      if (!button) {
        return;
      }
      var isActive = buttonMap[buttonId] === normalizeLifecycleFilter(activeFilter);
      button.classList.toggle("active", isActive);
      button.setAttribute("aria-pressed", isActive ? "true" : "false");
    });

  }

  function renderOrderLifecycleTable(orders) {
    var tbody = document.querySelector("#ordersTable tbody");
    if (!tbody) {
      return;
    }

    tbody.innerHTML = "";
    var filteredOrders = orders.filter(function (order) {
      return matchesLifecycleFilter(order, ORDER_LIFECYCLE_FILTER);
    });

    sortOrdersByNewestFirst(filteredOrders).forEach(function (order) {
      var row = document.createElement("tr");
      var assignedRider = order.assignedRider ? order.assignedRider : "-";
      var slot = order.pickupSlot || "-";
      var specs = order.vehicleType;

      row.innerHTML = "<td>" + order.id + "</td>" +
        "<td>" + (order.customerEmail || "-") + "</td>" +
        "<td>" + (order.sourceCity || "-") + " → " + (order.destinationCity || "-") + "</td>" +
        "<td>" + specs + "</td>" +
        "<td>" + order.vehicleType + "</td>" +
        "<td><strong>" + order.status + "</strong></td>" +
        "<td>" + assignedRider + "</td>" +
        "<td>" + slot + "</td>" +
        "<td><button class='btn btn-small' onclick=\"populateOrderFormForEdit(" + order.id + ", '" + String(order.sourceCity || "").replace(/'/g, "\\'") + "', '" + String(order.destinationCity || "").replace(/'/g, "\\'") + "', '" + String(order.vehicleType || "").replace(/'/g, "\\'") + "', '" + String(order.status || "").replace(/'/g, "\\'") + "', '" + String(order.weightKg || 0).replace(/'/g, "\\'") + "', '" + String(order.pickupSlot || "").replace(/'/g, "\\'") + "', '" + String(order.customerEmail || "").replace(/'/g, "\\'") + "')\">Load</button></td>";
      tbody.appendChild(row);
    });
  }

  function loadDashboardMetrics() {
    fetch(API_BASE + "/api/orders")
      .then(function (response) {
        return response.json();
      })
      .then(function (result) {
        var orders = result && result.orders && Array.isArray(result.orders) ? result.orders : [];
        var acceptedCount = 0;
        var onTheWayCount = 0;
        var deliveredCount = 0;

        orders.forEach(function (order) {
          var status = String(order.status || "").toUpperCase();
          if (status === "ACCEPTED") {
            acceptedCount += 1;
          } else if (status === "PICKED" || status === "IN_TRANSIT") {
            onTheWayCount += 1;
          } else if (status === "DELIVERED") {
            deliveredCount += 1;
          }
        });

        setText("totalOrdersTodayLabel", String(orders.length));
        setText("revenueTodayLabel", String(acceptedCount));
        setText("activeDeliveriesLabel", String(onTheWayCount));
        setText("avgRatingLabel", String(deliveredCount));
      })
      .catch(function (err) {
        console.error("Error loading dashboard metrics:", err);
      });
  }

  function saveSession(email, accountType) {
    var payload = {
      email: (email || "").trim().toLowerCase(),
      accountType: normalizeAccountType(accountType),
      createdAt: new Date().toISOString()
    };
    localStorage.setItem(SESSION_KEY, JSON.stringify(payload));
  }

  function readSession() {
    var raw = localStorage.getItem(SESSION_KEY);
    if (!raw) {
      return null;
    }
    try {
      var parsed = JSON.parse(raw);
      if (!parsed || !parsed.email) {
        return null;
      }
      return {
        email: String(parsed.email).toLowerCase(),
        accountType: normalizeAccountType(parsed.accountType)
      };
    } catch (err) {
      return null;
    }
  }

  function validEmail(email) {
    return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/.test(email || "");
  }

  async function postJson(path, payload) {
    var response = await fetch(API_BASE + path, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    var data;
    try {
      data = await response.json();
    } catch (err) {
      data = { success: false, message: "Invalid server response" };
    }

    return {
      ok: response.ok,
      status: response.status,
      data: data
    };
  }

  var ORDER_SYNC_CHANNEL_NAME = "drc-orders-sync-v1";
  var ORDER_SYNC_STORAGE_KEY = "drc_orders_sync_v1";

  function broadcastOrderChange() {
    var stamp = String(Date.now());

    if (window.BroadcastChannel) {
      try {
        var channel = new BroadcastChannel(ORDER_SYNC_CHANNEL_NAME);
        channel.postMessage({ type: "orders-changed", stamp: stamp });
        channel.close();
      } catch (err) {
        // Fallback below still keeps other tabs in sync.
      }
    }

    try {
      localStorage.setItem(ORDER_SYNC_STORAGE_KEY, stamp);
    } catch (err2) {
      // Ignore storage write failures.
    }
  }

  function listenForOrderChanges(callback) {
    if (window.BroadcastChannel) {
      try {
        var channel = new BroadcastChannel(ORDER_SYNC_CHANNEL_NAME);
        channel.addEventListener("message", function (event) {
          if (event && event.data && event.data.type === "orders-changed") {
            callback();
          }
        });
        return function () {
          channel.close();
        };
      } catch (err) {
        // Fall through to storage-event fallback.
      }
    }

    var storageHandler = function (event) {
      if (event.key === ORDER_SYNC_STORAGE_KEY) {
        callback();
      }
    };
    window.addEventListener("storage", storageHandler);
    return function () {
      window.removeEventListener("storage", storageHandler);
    };
  }

  function fallbackSeedAccounts() {
    return [
      { name: "Admin One", accountType: "ADMIN", email: "admin1@drc.local", password: "Admin@123", createdAt: "2026-04-23" },
      { name: "Admin Two", accountType: "ADMIN", email: "admin2@drc.local", password: "Admin@234", createdAt: "2026-04-23" },
      { name: "Admin Three", accountType: "ADMIN", email: "admin3@drc.local", password: "Admin@345", createdAt: "2026-04-23" },
      { name: "Admin Four", accountType: "ADMIN", email: "admin4@drc.local", password: "Admin@456", createdAt: "2026-04-23" },
      { name: "Demo Customer", accountType: "CUSTOMER", email: "customer@drc.local", password: "customer123", createdAt: "2026-04-23" },
      { name: "Demo Delivery", accountType: "DELIVERY_PARTNER", email: "delivery@drc.local", password: "delivery123", createdAt: "2026-04-23" }
    ];
  }

  function fallbackUsers() {
    var raw = localStorage.getItem(FALLBACK_USERS_KEY);
    if (!raw) {
      var seed = fallbackSeedAccounts();
      localStorage.setItem(FALLBACK_USERS_KEY, JSON.stringify(seed));
      return seed;
    }

    try {
      var parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        throw new Error("Invalid fallback auth data");
      }

      fallbackSeedAccounts().forEach(function (seedUser) {
        var exists = parsed.some(function (u) {
          return (u.email || "").toLowerCase() === seedUser.email.toLowerCase();
        });
        if (!exists) {
          parsed.push(seedUser);
        }
      });

      localStorage.setItem(FALLBACK_USERS_KEY, JSON.stringify(parsed));
      return parsed;
    } catch (e) {
      var reset = fallbackSeedAccounts();
      localStorage.setItem(FALLBACK_USERS_KEY, JSON.stringify(reset));
      return reset;
    }
  }

  function fallbackSaveUsers(users) {
    localStorage.setItem(FALLBACK_USERS_KEY, JSON.stringify(users));
  }

  function fallbackLogin(email, password, accountType) {
    var users = fallbackUsers();
    return users.find(function (u) {
      return (u.email || "").toLowerCase() === email.toLowerCase()
        && (u.password || "") === password
        && normalizeAccountType(u.accountType) === normalizeAccountType(accountType);
    });
  }

  function fallbackRegister(name, email, password, accountType) {
    var users = fallbackUsers();
    var exists = users.some(function (u) {
      return (u.email || "").toLowerCase() === email.toLowerCase();
    });
    if (exists) {
      return { success: false, message: "Email already exists" };
    }

    users.push({
      name: name,
      accountType: normalizeAccountType(accountType),
      email: email.toLowerCase(),
      password: password,
      createdAt: new Date().toISOString().slice(0, 19).replace("T", " ")
    });
    fallbackSaveUsers(users);
    return { success: true };
  }

  function normalizeAccountType(value) {
    var role = (value || "").toUpperCase();
    if (role === "DELIVERY" || role === "DELIVERY_PARTNER") {
      return "DELIVERY_PARTNER";
    }
    if (role === "ADMIN") {
      return "ADMIN";
    }
    return "CUSTOMER";
  }

  function getSelectedRoleFromLoginButtons() {
    var customerButton = byId("customerRoleButton");
    var deliveryButton = byId("deliveryRoleButton");
    if (deliveryButton && deliveryButton.classList.contains("is-selected")) {
      return "DELIVERY_PARTNER";
    }
    if (customerButton && customerButton.classList.contains("is-selected")) {
      return "CUSTOMER";
    }
    return "CUSTOMER";
  }

  function redirectToRoleDashboard(accountType) {
    var normalized = normalizeAccountType(accountType);
    var path = window.location.pathname || "";
    var inLoginSubFolder = path.indexOf("/login/") >= 0;
    var prefix = inLoginSubFolder ? "../" : "";

    if (normalized === "DELIVERY_PARTNER") {
      window.location.href = prefix + "delivery.html";
      return;
    }

    if (normalized === "ADMIN") {
      window.location.href = (path.indexOf("/login/admin/") >= 0 ? "../../" : "") + "admin.html";
      return;
    }

    window.location.href = prefix + "main.html";
  }

  function openModal(id) {
    var modal = byId(id);
    if (modal) {
      modal.classList.remove("hidden");
    }
  }

  function closeModal(id) {
    var modal = byId(id);
    if (modal) {
      modal.classList.add("hidden");
    }
  }

  function setupModalCloseButtons() {
    var closeButtons = document.querySelectorAll("[data-close-modal]");
    closeButtons.forEach(function (btn) {
      btn.addEventListener("click", function () {
        var target = btn.getAttribute("data-close-modal");
        if (target) {
          closeModal(target);
        }
      });
    });
  }

  function setupPasswordVisibilityToggles() {
    var toggles = document.querySelectorAll("[data-toggle-password]");
    toggles.forEach(function (toggle) {
      toggle.addEventListener("change", function () {
        var targetId = toggle.getAttribute("data-toggle-password");
        var target = byId(targetId);
        if (!target) {
          return;
        }
        target.type = toggle.checked ? "text" : "password";
      });
    });
  }

  function initLoginScreen() {
    var roleButtons = ["customerRoleButton", "deliveryRoleButton"]
      .map(byId)
      .filter(Boolean);

    roleButtons.forEach(function (btn) {
      btn.addEventListener("click", function () {
        roleButtons.forEach(function (b) {
          b.classList.remove("is-selected");
          b.setAttribute("aria-pressed", "false");
        });
        btn.classList.add("is-selected");
        btn.setAttribute("aria-pressed", "true");
      });
    });

    if (roleButtons.length) {
      roleButtons[0].classList.add("is-selected");
      roleButtons[0].setAttribute("aria-pressed", "true");
    }

    var signInButton = byId("signInButton");
    var errorLabel = byId("errorLabel");
    var emailField = byId("emailField");
    var passwordField = byId("passwordField");
    var createAccountLink = byId("createAccountLink");
    var createAccountError = byId("createAccountError");
    var createAccountSubmitButton = byId("createAccountSubmitButton");

    if (signInButton && errorLabel) {
      signInButton.addEventListener("click", async function () {
        var email = (emailField && emailField.value ? emailField.value : "").trim().toLowerCase();
        var password = (passwordField && passwordField.value ? passwordField.value : "").trim();
        var selectedType = getSelectedRoleFromLoginButtons();

        if (!validEmail(email) || password.length < 1) {
          errorLabel.textContent = "Enter valid email and password";
          show(errorLabel);
          return;
        }

        var result;
        try {
          result = await postJson("/api/auth/login", {
            email: email,
            password: password,
            accountType: selectedType
          });
        } catch (err) {
          var fallbackUser = fallbackLogin(email, password, selectedType);
          if (!fallbackUser) {
            errorLabel.textContent = "Invalid credentials for selected role";
            show(errorLabel);
            return;
          }
          hide(errorLabel);
          saveSession(fallbackUser.email, selectedType);
          redirectToRoleDashboard(selectedType);
          return;
        }

        if (!result.ok || !result.data || !result.data.success) {
          errorLabel.textContent = (result.data && result.data.message) ? result.data.message : "Invalid credentials for selected role";
          show(errorLabel);
          return;
        }

        hide(errorLabel);
        saveSession(result.data.email || email, result.data.accountType || selectedType);
        redirectToRoleDashboard(result.data.accountType || selectedType);
      });
    }

    if (createAccountLink) {
      createAccountLink.addEventListener("click", function () {
        openModal("createAccountModal");
      });
    }

    if (createAccountSubmitButton) {
      createAccountSubmitButton.addEventListener("click", async function () {
        var newNameField = byId("newNameField");
        var newRoleField = byId("newRoleField");
        var newEmailField = byId("newEmailField");
        var newPasswordField = byId("newPasswordField");
        var newConfirmPasswordField = byId("newConfirmPasswordField");

        var name = (newNameField && newNameField.value ? newNameField.value : "").trim();
        var accountType = normalizeAccountType(newRoleField && newRoleField.value ? newRoleField.value : "CUSTOMER");
        var email = (newEmailField && newEmailField.value ? newEmailField.value : "").trim().toLowerCase();
        var password = (newPasswordField && newPasswordField.value ? newPasswordField.value : "").trim();
        var confirm = (newConfirmPasswordField && newConfirmPasswordField.value ? newConfirmPasswordField.value : "").trim();

        if (!createAccountError) {
          return;
        }

        if (name.length < 2) {
          createAccountError.textContent = "Name must be at least 2 characters";
          show(createAccountError);
          return;
        }

        if (!validEmail(email)) {
          createAccountError.textContent = "Enter a valid email address";
          show(createAccountError);
          return;
        }

        if (password.length < 4) {
          createAccountError.textContent = "Password must be at least 4 characters";
          show(createAccountError);
          return;
        }

        if (password !== confirm) {
          createAccountError.textContent = "Password and confirm password do not match";
          show(createAccountError);
          return;
        }

        if (accountType === "ADMIN") {
          createAccountError.textContent = "Admin signup is disabled";
          show(createAccountError);
          return;
        }

        var registerResult;
        try {
          registerResult = await postJson("/api/auth/register", {
            name: name,
            accountType: accountType,
            email: email,
            password: password
          });
        } catch (err) {
          var fallbackResult = fallbackRegister(name, email, password, accountType);
          if (!fallbackResult.success) {
            createAccountError.textContent = fallbackResult.message || "Could not create account";
            show(createAccountError);
            return;
          }
          hide(createAccountError);
          closeModal("createAccountModal");
          if (emailField) {
            emailField.value = email;
          }
          if (passwordField) {
            passwordField.value = "";
          }
          return;
        }

        if (!registerResult.ok || !registerResult.data || !registerResult.data.success) {
          createAccountError.textContent = (registerResult.data && registerResult.data.message) ? registerResult.data.message : "Could not create account";
          show(createAccountError);
          return;
        }

        hide(createAccountError);
        closeModal("createAccountModal");
        if (emailField) {
          emailField.value = email;
        }
        if (passwordField) {
          passwordField.value = "";
        }
      });
    }
  }

  function initAdminLoginScreen() {
    var signInButton = byId("adminSignInButton");
    var emailField = byId("adminEmailField");
    var passwordField = byId("adminPasswordField");
    var errorLabel = byId("adminErrorLabel");
    if (!signInButton || !emailField || !passwordField || !errorLabel) {
      return;
    }

    signInButton.addEventListener("click", async function () {
      var email = (emailField.value || "").trim().toLowerCase();
      var password = (passwordField.value || "").trim();

      var result;
      try {
        result = await postJson("/api/auth/login", {
          email: email,
          password: password,
          accountType: "ADMIN"
        });
      } catch (err) {
        var fallbackUser = fallbackLogin(email, password, "ADMIN");
        if (!fallbackUser) {
          errorLabel.textContent = "Invalid admin credentials";
          show(errorLabel);
          return;
        }
        hide(errorLabel);
        redirectToRoleDashboard("ADMIN");
        return;
      }

      if (!result.ok || !result.data || !result.data.success) {
        errorLabel.textContent = (result.data && result.data.message) ? result.data.message : "Invalid admin credentials";
        show(errorLabel);
        return;
      }

      hide(errorLabel);
      saveSession(result.data.email || email, "ADMIN");
      redirectToRoleDashboard("ADMIN");
    });
  }

  function initMainScreen() {
    var loginButton = byId("loginButton");
    var signupButton = byId("signupButton");
    var homePanel = byId("homePanel");
    var appPanel = byId("appPanel");
    var homeCenterPane = byId("homeCenterPane");

    function enterApp(username) {
      hide(homePanel);
      hide(homeCenterPane);
      show(appPanel);
      setText("loggedInUserLabel", "Logged in: " + username + " (CUSTOMER)");
      setText("statusLabel", "Welcome " + username + "!");
    }

    var existingSession = readSession();
    if (existingSession && existingSession.email) {
      if (existingSession.accountType === "CUSTOMER") {
        enterApp(existingSession.email);
      } else if (existingSession.accountType === "DELIVERY_PARTNER") {
        redirectToRoleDashboard("DELIVERY_PARTNER");
      } else if (existingSession.accountType === "ADMIN") {
        redirectToRoleDashboard("ADMIN");
      }
    }

    if (loginButton) {
      loginButton.addEventListener("click", function () {
        openModal("loginModal");
      });
    }

    if (signupButton) {
      signupButton.addEventListener("click", function () {
        openModal("signupModal");
      });
    }

    var loginDialogSubmitButton = byId("loginDialogSubmitButton");
    if (loginDialogSubmitButton) {
      loginDialogSubmitButton.addEventListener("click", async function () {
        var emailValue = (byId("loginEmailField") && byId("loginEmailField").value) ? byId("loginEmailField").value.trim().toLowerCase() : "";
        var passwordValue = (byId("loginPasswordField") && byId("loginPasswordField").value) ? byId("loginPasswordField").value.trim() : "";
        var roleValue = normalizeAccountType((byId("loginRoleCombo") && byId("loginRoleCombo").value) || "Customer");

        var result;
        try {
          result = await postJson("/api/auth/login", {
            email: emailValue,
            password: passwordValue,
            accountType: roleValue
          });
        } catch (err) {
          var fallbackUser = fallbackLogin(emailValue, passwordValue, roleValue);
          if (!fallbackUser) {
            return;
          }
          closeModal("loginModal");
          saveSession(fallbackUser.email, roleValue);
          enterApp(fallbackUser.email);
          return;
        }

        if (!result.ok || !result.data || !result.data.success) {
          return;
        }

        closeModal("loginModal");
        saveSession(result.data.email || emailValue, roleValue);
        enterApp(result.data.email || emailValue);
      });
    }

    var signupDialogSubmitButton = byId("signupDialogSubmitButton");
    if (signupDialogSubmitButton) {
      signupDialogSubmitButton.addEventListener("click", async function () {
        var nameValue = (byId("signupNameField") && byId("signupNameField").value) ? byId("signupNameField").value.trim() : "";
        var emailValue = (byId("signupEmailField") && byId("signupEmailField").value) ? byId("signupEmailField").value.trim().toLowerCase() : "";
        var passwordValue = (byId("signupPasswordField") && byId("signupPasswordField").value) ? byId("signupPasswordField").value.trim() : "";
        var confirmValue = (byId("signupConfirmField") && byId("signupConfirmField").value) ? byId("signupConfirmField").value.trim() : "";
        var roleValue = normalizeAccountType((byId("signupRoleCombo") && byId("signupRoleCombo").value) || "Customer");

        if (nameValue.length < 2 || !validEmail(emailValue) || passwordValue.length < 4 || passwordValue !== confirmValue || roleValue === "ADMIN") {
          return;
        }

        var registerResult;
        try {
          registerResult = await postJson("/api/auth/register", {
            name: nameValue,
            email: emailValue,
            password: passwordValue,
            accountType: roleValue
          });
        } catch (err) {
          var fallbackResult = fallbackRegister(nameValue, emailValue, passwordValue, roleValue);
          if (!fallbackResult.success) {
            return;
          }
          closeModal("signupModal");
          saveSession(emailValue, roleValue);
          enterApp(emailValue);
          return;
        }

        if (!registerResult.ok || !registerResult.data || !registerResult.data.success) {
          return;
        }

        closeModal("signupModal");
        saveSession(emailValue, roleValue);
        enterApp(emailValue);
      });
    }

    var bellLabel = byId("bellLabel");
    var unreadBadgeLabel = byId("unreadBadgeLabel");
    if (bellLabel && unreadBadgeLabel) {
      bellLabel.addEventListener("click", function () {
        openModal("notificationsModal");
      });
    }

    var markAllReadButton = byId("markAllReadButton");
    if (markAllReadButton) {
      markAllReadButton.addEventListener("click", function () {
        unreadBadgeLabel.textContent = "0";
        closeModal("notificationsModal");
      });
    }

    var viewHistoryButton = byId("viewHistoryButton");
    if (viewHistoryButton) {
      viewHistoryButton.addEventListener("click", function () {
        openModal("routeHistoryModal");
      });
    }

    var viewOrdersButton = byId("viewOrdersButton");
    if (viewOrdersButton) {
      viewOrdersButton.addEventListener("click", function () {
        openModal("customerOrderHistoryModal");
      });
    }

    var ratingSubmitButton = byId("ratingSubmitButton");
    if (ratingSubmitButton) {
      ratingSubmitButton.addEventListener("click", function () {
        closeModal("ratingModal");
      });
    }

    var reviewArea = byId("reviewArea");
    if (reviewArea) {
      reviewArea.addEventListener("input", function () {
        if (reviewArea.value.length > 200) {
          reviewArea.value = reviewArea.value.substring(0, 200);
        }
      });
    }

    var starButtons = document.querySelectorAll(".star-btn[data-star]");
    starButtons.forEach(function (btn) {
      btn.addEventListener("click", function () {
        var value = Number(btn.getAttribute("data-star") || "0");
        starButtons.forEach(function (star) {
          var starValue = Number(star.getAttribute("data-star") || "0");
          star.style.opacity = starValue <= value ? "1" : "0.35";
        });
      });
    });

    // Initialize customer dashboard with city dropdowns and predefined route pricing inputs
    var pickupCitySelect = byId("pickupCity");
    var dropCitySelect = byId("dropCity");
    var estimateCostButton = byId("estimateCostButton");
    var costSummaryText = byId("costSummaryText");
    var statusLabel = byId("statusLabel");
    var dimensionUnitSelect = byId("dimensionUnit");
    var dimensionPresetSelect = byId("dimensionPreset");
    var dimensionPresetPanel = byId("dimensionPresetPanel");
    var dimensionCustomPanel = byId("dimensionCustomPanel");
    var presetHint = byId("presetHint");
    var lengthRange = byId("lengthRange");
    var widthRange = byId("widthRange");
    var heightRange = byId("heightRange");
    var lengthValueLabel = byId("lengthValueLabel");
    var widthValueLabel = byId("widthValueLabel");
    var heightValueLabel = byId("heightValueLabel");
    var dimensionModeButtons = document.querySelectorAll("#dimensionModeGroup [data-dimension-mode]");
    var activeDimensionMode = "preset";

    var CITIES = [
      { name: "Mumbai", lat: 19.076, lng: 72.8777 },
      { name: "Pune", lat: 18.5204, lng: 73.8567 },
      { name: "Aurangabad", lat: 19.8762, lng: 75.3433 },
      { name: "Nashik", lat: 19.9975, lng: 73.7898 },
      { name: "Nagpur", lat: 21.1458, lng: 79.0882 },
      { name: "Ahmedabad", lat: 23.0225, lng: 72.5714 },
      { name: "Indore", lat: 22.7196, lng: 75.8577 },
      { name: "Bhopal", lat: 23.1815, lng: 79.9864 }
    ];

    var VEHICLE_CAPACITIES = {
      Bike: 10,
      Van: 100,
      Truck: 1000
    };

    var PREDEFINED_ROUTE_ESTIMATES = {
      "mumbai|pune": { distanceKm: 150, tollAmount: 35 },
      "mumbai|nashik": { distanceKm: 167, tollAmount: 45 },
      "mumbai|aurangabad": { distanceKm: 335, tollAmount: 95 },
      "pune|nashik": { distanceKm: 211, tollAmount: 55 },
      "pune|aurangabad": { distanceKm: 235, tollAmount: 60 },
      "nagpur|bhopal": { distanceKm: 352, tollAmount: 90 },
      "indore|ahmedabad": { distanceKm: 400, tollAmount: 110 },
      "indore|bhopal": { distanceKm: 195, tollAmount: 45 },
      "ahmedabad|mumbai": { distanceKm: 525, tollAmount: 150 }
    };

    // Populate city dropdowns
    if (pickupCitySelect && dropCitySelect) {
      CITIES.forEach(function (city) {
        var option1 = document.createElement("option");
        option1.value = city.name;
        option1.textContent = city.name;
        pickupCitySelect.appendChild(option1);

        var option2 = document.createElement("option");
        option2.value = city.name;
        option2.textContent = city.name;
        dropCitySelect.appendChild(option2);
      });

      pickupCitySelect.addEventListener("change", function () {
        if (statusLabel) {
          statusLabel.textContent = "Pickup/drop selected. Click Estimate Cost.";
        }
      });
      dropCitySelect.addEventListener("change", function () {
        if (statusLabel) {
          statusLabel.textContent = "Pickup/drop selected. Click Estimate Cost.";
        }
      });
    }

    function findCityByName(name) {
      return CITIES.find(function (city) {
        return city.name === name;
      }) || null;
    }

    function getVehicleCapacityKg(vehicleType) {
      return VEHICLE_CAPACITIES[vehicleType] || 0;
    }

    function isWeightWithinCapacity(vehicleType, weightKg) {
      return weightKg > 0 && weightKg <= getVehicleCapacityKg(vehicleType);
    }

    function normalizeRouteKey(cityA, cityB) {
      var pair = [String(cityA || "").toLowerCase(), String(cityB || "").toLowerCase()].sort();
      return pair[0] + "|" + pair[1];
    }

    function toRadians(value) {
      return value * Math.PI / 180;
    }

    function estimateDistanceFromCoordinates(fromCity, toCity) {
      var earthRadiusKm = 6371;
      var dLat = toRadians(toCity.lat - fromCity.lat);
      var dLng = toRadians(toCity.lng - fromCity.lng);
      var lat1 = toRadians(fromCity.lat);
      var lat2 = toRadians(toCity.lat);

      var a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
      var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      var straightLine = earthRadiusKm * c;

      // Inflate straight-line distance to approximate real road distance.
      return Math.round(straightLine * 1.26);
    }

    function estimateTollFromDistance(distanceKm) {
      if (distanceKm <= 120) {
        return 15;
      }
      if (distanceKm <= 220) {
        return 45;
      }
      if (distanceKm <= 360) {
        return 85;
      }
      return 125;
    }

    function getRouteEstimate(pickupCity, dropCity) {
      if (!pickupCity || !dropCity || pickupCity === dropCity) {
        return null;
      }

      var routeKey = normalizeRouteKey(pickupCity, dropCity);
      if (PREDEFINED_ROUTE_ESTIMATES[routeKey]) {
        return PREDEFINED_ROUTE_ESTIMATES[routeKey];
      }

      var pickup = findCityByName(pickupCity);
      var drop = findCityByName(dropCity);
      if (!pickup || !drop) {
        return null;
      }

      var fallbackDistance = estimateDistanceFromCoordinates(pickup, drop);
      return {
        distanceKm: fallbackDistance,
        tollAmount: estimateTollFromDistance(fallbackDistance)
      };
    }

    function parseNumber(value, fallback) {
      var parsed = Number(String(value || "").trim());
      return Number.isFinite(parsed) ? parsed : fallback;
    }

    var PRESET_SIZES = {
      cm: {
        Small: { length: 20, width: 15, height: 10 },
        Medium: { length: 40, width: 30, height: 20 },
        Large: { length: 70, width: 50, height: 40 },
        XL: { length: 120, width: 80, height: 60 }
      },
      ft: {
        Small: { length: 1.5, width: 1, height: 1 },
        Medium: { length: 3, width: 2, height: 2 },
        Large: { length: 5, width: 3, height: 3 },
        XL: { length: 7, width: 4, height: 4 }
      }
    };

    function getUnitKey() {
      if (dimensionUnitSelect && dimensionUnitSelect.value === "ft") {
        return "ft";
      }
      return "cm";
    }

    function updateSliderRanges() {
      var unitKey = getUnitKey();
      var config = unitKey === "ft"
        ? { min: 1, max: 8, step: 0.5 }
        : { min: 5, max: 200, step: 1 };

      [lengthRange, widthRange, heightRange].forEach(function (range) {
        if (!range) {
          return;
        }
        range.min = config.min;
        range.max = config.max;
        range.step = config.step;
      });
    }

    function formatDimensionValue(value, unitKey) {
      if (unitKey === "ft") {
        return value.toFixed(1) + " ft";
      }
      return Math.round(value) + " cm";
    }

    function syncSliderLabels() {
      var unitKey = getUnitKey();
      if (lengthRange && lengthValueLabel) {
        lengthValueLabel.textContent = formatDimensionValue(parseNumber(lengthRange.value, 0), unitKey);
      }
      if (widthRange && widthValueLabel) {
        widthValueLabel.textContent = formatDimensionValue(parseNumber(widthRange.value, 0), unitKey);
      }
      if (heightRange && heightValueLabel) {
        heightValueLabel.textContent = formatDimensionValue(parseNumber(heightRange.value, 0), unitKey);
      }
    }

    function applyPreset() {
      if (!dimensionPresetSelect) {
        return;
      }
      var unitKey = getUnitKey();
      var presetName = dimensionPresetSelect.value || "Small";
      var preset = PRESET_SIZES[unitKey][presetName] || PRESET_SIZES[unitKey].Small;

      if (lengthRange) {
        lengthRange.value = preset.length;
      }
      if (widthRange) {
        widthRange.value = preset.width;
      }
      if (heightRange) {
        heightRange.value = preset.height;
      }

      if (presetHint) {
        presetHint.textContent = presetName + ": " + preset.length + " x " + preset.width + " x " + preset.height + " " + unitKey;
      }

      syncSliderLabels();
    }

    function setDimensionMode(mode) {
      activeDimensionMode = mode;
      dimensionModeButtons.forEach(function (btn) {
        var isActive = btn.getAttribute("data-dimension-mode") === mode;
        btn.classList.toggle("is-selected", isActive);
        btn.setAttribute("aria-pressed", isActive ? "true" : "false");
      });
      if (dimensionPresetPanel && dimensionCustomPanel) {
        if (mode === "preset") {
          dimensionPresetPanel.classList.remove("hidden");
          dimensionCustomPanel.classList.add("hidden");
          applyPreset();
        } else {
          dimensionPresetPanel.classList.add("hidden");
          dimensionCustomPanel.classList.remove("hidden");
          syncSliderLabels();
        }
      }
    }

    if (dimensionModeButtons.length) {
      dimensionModeButtons.forEach(function (btn) {
        btn.addEventListener("click", function () {
          setDimensionMode(btn.getAttribute("data-dimension-mode"));
        });
      });
    }

    if (dimensionPresetSelect) {
      dimensionPresetSelect.addEventListener("change", function () {
        if (activeDimensionMode === "preset") {
          applyPreset();
        }
      });
    }

    if (dimensionUnitSelect) {
      dimensionUnitSelect.addEventListener("change", function () {
        updateSliderRanges();
        if (activeDimensionMode === "preset") {
          applyPreset();
        } else {
          syncSliderLabels();
        }
      });
    }

    [lengthRange, widthRange, heightRange].forEach(function (range) {
      if (!range) {
        return;
      }
      range.addEventListener("input", syncSliderLabels);
    });

    updateSliderRanges();
    if (dimensionPresetSelect) {
      applyPreset();
    }
    if (dimensionModeButtons.length) {
      setDimensionMode("preset");
    }

    function getDimensionSummary() {
      var unitKey = getUnitKey();
      if (activeDimensionMode === "preset" && dimensionPresetSelect) {
        var presetName = dimensionPresetSelect.value || "Small";
        var preset = PRESET_SIZES[unitKey][presetName] || PRESET_SIZES[unitKey].Small;
        return presetName + " (" + preset.length + " x " + preset.width + " x " + preset.height + " " + unitKey + ")";
      }

      var lengthVal = lengthRange ? parseNumber(lengthRange.value, 0) : 0;
      var widthVal = widthRange ? parseNumber(widthRange.value, 0) : 0;
      var heightVal = heightRange ? parseNumber(heightRange.value, 0) : 0;
      return "Custom (" + lengthVal + " x " + widthVal + " x " + heightVal + " " + unitKey + ")";
    }

    function buildBookingEstimate() {
      var pickupCity = pickupCitySelect ? pickupCitySelect.value : "";
      var dropCity = dropCitySelect ? dropCitySelect.value : "";
      var routeEstimate = getRouteEstimate(pickupCity, dropCity);

      if (!routeEstimate) {
        return null;
      }

      var vehicleType = byId("vehicleType") ? byId("vehicleType").value : "Bike";
      var vehicleCosts = {
        "Bike": 2,
        "Van": 3.5,
        "Truck": 5
      };
      var weightKg = parseNumber(byId("weightKg") ? byId("weightKg").value : 0, 0);
      var capacityKg = getVehicleCapacityKg(vehicleType);

      if (!isWeightWithinCapacity(vehicleType, weightKg)) {
        if (statusLabel) {
          statusLabel.textContent = vehicleType + " can carry up to " + capacityKg + " kg. Reduce the weight before booking.";
        }
        if (costSummaryText) {
          costSummaryText.value = vehicleType + " can carry up to " + capacityKg + " kg. Current weight: " + weightKg + " kg.";
        }
        return null;
      }

      var rate = vehicleCosts[vehicleType] || 2;
      var distanceKm = routeEstimate.distanceKm;
      var tollCost = routeEstimate.tollAmount;
      var totalCost = (distanceKm * rate) + tollCost;

      return {
        pickupCity: pickupCity,
        dropCity: dropCity,
        vehicleType: vehicleType,
        rate: rate,
        distanceKm: distanceKm,
        tollCost: tollCost,
        totalCost: totalCost,
        orderType: byId("orderType") ? byId("orderType").value : "",
        weightKg: weightKg,
        pickupSlot: byId("pickupSlot") ? byId("pickupSlot").value : "",
        capacityKg: capacityKg,
        dimensionSummary: getDimensionSummary()
      };
    }

    function calculateCost() {
      var estimate = buildBookingEstimate();
      if (!estimate) {
        if (statusLabel) {
          statusLabel.textContent = "Select different pickup and drop cities to estimate cost.";
        }
        if (costSummaryText) {
          costSummaryText.value = "Pickup and drop cities must be selected and cannot be the same.";
        }
        return;
      }

      var summary = "Route: " + (estimate.pickupCity || "-") + " -> " + (estimate.dropCity || "-") + "\n"
        + "Order Type: " + (estimate.orderType || "-") + "\n"
        + "Weight: " + (estimate.weightKg || 0) + " kg\n"
        + "Vehicle Capacity: " + (estimate.capacityKg || 0) + " kg\n"
        + "Dimensions: " + estimate.dimensionSummary + "\n"
        + "Vehicle: " + estimate.vehicleType + "\n"
        + "Distance (predefined): " + estimate.distanceKm.toFixed(1) + " km\n"
        + "Price per km: " + estimate.rate.toFixed(2) + "\n"
        + "Toll (predefined): " + estimate.tollCost.toFixed(2) + "\n"
        + "-------------------\n"
        + "TOTAL COST: " + estimate.totalCost.toFixed(2);

      if (costSummaryText) {
        costSummaryText.value = summary;
      }
      if (statusLabel) {
        statusLabel.textContent = "Estimate ready: " + estimate.totalCost.toFixed(2) + " INR";
      }

      return estimate;
    }

    function loadAndDisplayCustomerOrders() {
      var session = readSession();
      var customerEmail = session && session.email ? session.email : "";

      fetch(API_BASE + "/api/orders")
        .then(function (response) {
          return response.json();
        })
        .then(function (result) {
          var tbody = document.querySelector("#customerOrdersTable tbody");
          if (!tbody) {
            return;
          }

          tbody.innerHTML = "";
          if (!result.success || !Array.isArray(result.orders)) {
            return;
          }

          var rows = result.orders.filter(function (order) {
            return !customerEmail || String(order.customerEmail || "").toLowerCase() === customerEmail.toLowerCase();
          });

          rows.slice(0, 12).forEach(function (order) {
            var row = document.createElement("tr");
            row.innerHTML = "<td>" + order.id + "</td>" +
              "<td>" + (order.sourceCity || "-") + " → " + (order.destinationCity || "-") + "</td>" +
              "<td>" + (order.orderType || order.vehicleType || "-") + "</td>" +
              "<td><strong>" + (order.status || "-") + "</strong></td>" +
              "<td>" + (order.pickupSlot || "-") + "</td>" +
              "<td>INR " + Number(order.totalCost || 0).toFixed(2) + "</td>" +
              "<td><button class='btn btn-small btn-blue' type='button' onclick=\"populateBookingFormFromOrder(" + order.id + ")\">View</button></td>";
            tbody.appendChild(row);
          });
        })
        .catch(function (err) {
          console.error("Error loading customer orders:", err);
        });
    }

    function populateBookingFormFromOrder(orderId) {
      fetch(API_BASE + "/api/orders?id=" + orderId)
        .then(function (response) {
          return response.json();
        })
        .then(function (result) {
          if (!result.success || !result.order) {
            return;
          }

          var order = result.order;
          if (pickupCitySelect) {
            pickupCitySelect.value = order.sourceCity || "";
          }
          if (dropCitySelect) {
            dropCitySelect.value = order.destinationCity || "";
          }
          if (byId("vehicleType")) {
            byId("vehicleType").value = order.vehicleType || "Bike";
          }
          if (byId("weightKg")) {
            byId("weightKg").value = order.weightKg || 0;
          }
          if (byId("pickupSlot")) {
            byId("pickupSlot").value = order.pickupSlot || "";
          }
          calculateCost();
        })
        .catch(function (err) {
          console.error("Error loading order details:", err);
        });
    }

    window.populateBookingFormFromOrder = populateBookingFormFromOrder;

    function handleBookPickup() {
      var session = readSession();
      var customerEmail = session && session.email ? session.email : "";
      if (!customerEmail) {
        alert("Please log in before booking.");
        return;
      }

      var estimate = buildBookingEstimate();
      if (!estimate) {
        alert("Please select a valid route before booking.");
        return;
      }

      var payload = {
        customerEmail: customerEmail,
        pickupCity: estimate.pickupCity,
        dropCity: estimate.dropCity,
        vehicleType: estimate.vehicleType,
        weightKg: estimate.weightKg,
        pickupSlot: estimate.pickupSlot,
        totalCost: estimate.totalCost
      };

      postJson("/api/orders", payload)
        .then(function (result) {
          if (result.ok && result.data && result.data.success) {
            alert("Pickup booked successfully! Order ID: " + result.data.orderId);
            if (statusLabel) {
              statusLabel.textContent = "Booking saved to database.";
            }
            broadcastOrderChange();
            loadAndDisplayCustomerOrders();
            calculateCost();
          } else {
            alert("Error booking pickup: " + ((result.data && result.data.message) || "Unknown error"));
          }
        })
        .catch(function (err) {
          alert("Network error: " + err.message);
        });
    }

    function loadAndDisplayDeliveryOrders() {
      var session = readSession();
      var deliveryEmail = session && session.email ? session.email : "";

      fetch(API_BASE + "/api/orders")
        .then(function (response) {
          return response.json();
        })
        .then(function (result) {
          if (!result.success || !Array.isArray(result.orders)) {
            return;
          }

          var availableTbody = document.querySelector("#deliveryAvailableTable tbody");
          var assignedTbody = document.querySelector("#deliveryMyOrdersTable tbody");

          if (availableTbody) {
            availableTbody.innerHTML = "";
          }
          if (assignedTbody) {
            assignedTbody.innerHTML = "";
          }

          var availableOrders = result.orders.filter(function (order) {
            return order.status === "CREATED" || order.status === "PLACED";
          }).slice(0, 12);

          var assignedOrders = result.orders.filter(function (order) {
            var assignedUser = String(order.assignedRider || "").toLowerCase();
            var isAssignedToMe = deliveryEmail && assignedUser === deliveryEmail.toLowerCase();
            var isActiveDeliveryState = ["ACCEPTED", "PICKED", "IN_TRANSIT"].indexOf(order.status) !== -1;
            return isAssignedToMe || isActiveDeliveryState;
          }).slice(0, 12);

          availableOrders.forEach(function (order) {
            if (!availableTbody) {
              return;
            }
            var row = document.createElement("tr");
            row.innerHTML = "<td>" + order.id + "</td>" +
              "<td>" + (order.sourceCity || "-") + " → " + (order.destinationCity || "-") + "</td>" +
              "<td>" + (order.weightKg || 0) + " kg</td>" +
              "<td>" + (order.vehicleType || "-") + "</td>" +
              "<td><strong>" + (order.status || "-") + "</strong></td>" +
              "<td>INR " + Number(order.totalCost || 0).toFixed(2) + "</td>" +
              "<td><button class='btn btn-small btn-blue' type='button' onclick=\"populateBookingFormFromOrder(" + order.id + ")\">View</button></td>";
            availableTbody.appendChild(row);
          });

          assignedOrders.forEach(function (order) {
            if (!assignedTbody) {
              return;
            }
            var row = document.createElement("tr");
            row.innerHTML = "<td>" + order.id + "</td>" +
              "<td>" + (order.sourceCity || "-") + " → " + (order.destinationCity || "-") + "</td>" +
              "<td>" + (order.status || "-") + "</td>" +
              "<td>" + (order.pickupSlot || "-") + "</td>" +
              "<td><button class='btn btn-small btn-blue' type='button' onclick=\"populateBookingFormFromOrder(" + order.id + ")\">View</button></td>";
            assignedTbody.appendChild(row);
          });
        })
        .catch(function (err) {
          console.error("Error loading delivery orders:", err);
        });
    }

    if (estimateCostButton) {
      estimateCostButton.addEventListener("click", calculateCost);
    }

    var placeOrderButton = byId("placeOrderButton");
    if (placeOrderButton) {
      placeOrderButton.addEventListener("click", handleBookPickup);
    }

    var vehicleTypeSelect = byId("vehicleType");
    if (vehicleTypeSelect) {
      vehicleTypeSelect.addEventListener("change", function () {
        var pickupCity = pickupCitySelect ? pickupCitySelect.value : "";
        var dropCity = dropCitySelect ? dropCitySelect.value : "";
        if (getRouteEstimate(pickupCity, dropCity)) {
          calculateCost();
        }
      });
    }

    loadAndDisplayCustomerOrders();
    listenForOrderChanges(function () {
      loadAndDisplayCustomerOrders();
    });
  }

  function loadAndDisplayDeliveryOrders() {
    var session = readSession();
    var deliveryEmail = session && session.email ? session.email : "";

    fetch(API_BASE + "/api/orders")
      .then(function (response) {
        return response.json();
      })
      .then(function (result) {
        if (!result.success || !Array.isArray(result.orders)) {
          return;
        }

        DELIVERY_ORDER_CACHE = {};

        var availableTbody = document.querySelector("#deliveryAvailableTable tbody");
        var assignedTbody = document.querySelector("#deliveryMyOrdersTable tbody");

        if (availableTbody) {
          availableTbody.innerHTML = "";
        }
        if (assignedTbody) {
          assignedTbody.innerHTML = "";
        }

        var availableOrders = result.orders.filter(function (order) {
          return order.status === "CREATED" || order.status === "PLACED";
        }).slice(0, 12);

        var assignedOrders = result.orders.filter(function (order) {
          var assignedUser = String(order.assignedRider || "").toLowerCase();
          var isAssignedToMe = deliveryEmail && assignedUser === deliveryEmail.toLowerCase();
          var isActiveDeliveryState = ["ACCEPTED", "PICKED", "IN_TRANSIT"].indexOf(order.status) !== -1;
          return isAssignedToMe || isActiveDeliveryState;
        }).slice(0, 12);

        availableOrders.forEach(function (order) {
          if (!availableTbody) {
            return;
          }
          DELIVERY_ORDER_CACHE[String(order.id)] = order;
          var row = document.createElement("tr");
          row.innerHTML = "<td>" + order.id + "</td>" +
            "<td>" + (order.sourceCity || "-") + " → " + (order.destinationCity || "-") + "</td>" +
            "<td>" + (order.weightKg || 0) + " kg</td>" +
            "<td>" + (order.vehicleType || "-") + "</td>" +
            "<td><strong>" + (order.status || "-") + "</strong></td>" +
            "<td>INR " + Number(order.totalCost || 0).toFixed(2) + "</td>" +
            "<td><button class='btn btn-small btn-blue' type='button' onclick=\"selectDeliveryOrder(" + order.id + ")\">Select</button> <button class='btn btn-small btn-primary' type='button' onclick=\"acceptDeliveryOrder(" + order.id + ")\">Accept</button></td>";
          availableTbody.appendChild(row);
        });

        assignedOrders.forEach(function (order) {
          if (!assignedTbody) {
            return;
          }
          DELIVERY_ORDER_CACHE[String(order.id)] = order;
          var assignedUser = String(order.assignedRider || "").toLowerCase();
          var canRelease = order.status === "ACCEPTED" && deliveryEmail && assignedUser === deliveryEmail.toLowerCase();
          var row = document.createElement("tr");
          row.innerHTML = "<td>" + order.id + "</td>" +
            "<td>" + (order.sourceCity || "-") + " → " + (order.destinationCity || "-") + "</td>" +
            "<td>" + (order.status || "-") + "</td>" +
            "<td>" + (order.pickupSlot || "-") + "</td>" +
            "<td><button class='btn btn-small btn-blue' type='button' onclick=\"selectDeliveryOrder(" + order.id + ")\">Route</button>" + (canRelease ? " <button class='btn btn-small btn-orange' type='button' onclick=\"releaseAcceptedDeliveryOrder(" + order.id + ")\">Remove</button>" : "") + "</td>";
          assignedTbody.appendChild(row);
        });

        if (DELIVERY_SELECTED_ORDER_ID && DELIVERY_ORDER_CACHE[String(DELIVERY_SELECTED_ORDER_ID)]) {
          selectDeliveryOrder(DELIVERY_SELECTED_ORDER_ID);
        }
      })
      .catch(function (err) {
        console.error("Error loading delivery orders:", err);
      });
  }

  function getMapFrame() {
    var frame = byId("mapWebView");
    if (!frame || !frame.contentWindow) {
      return null;
    }
    return frame.contentWindow;
  }

  function updateDeliverySelectionPanel(order, routeInfo) {
    var label = byId("deliverySelectedOrderLabel");
    var meta = byId("deliverySelectedOrderMeta");
    if (label) {
      label.textContent = "Order #" + order.id + " | " + (order.sourceCity || "-") + " → " + (order.destinationCity || "-") + " | " + (order.vehicleType || "-") + " | " + (order.weightKg || 0) + " kg";
    }
    if (meta) {
      if (routeInfo && routeInfo.distanceKm) {
        meta.textContent = "Best route fetched from OpenStreetMap. Distance: " + routeInfo.distanceKm.toFixed(1) + " km.";
      } else {
        meta.textContent = "OpenStreetMap route preview will appear on the map.";
      }
    }
  }

  function drawRouteOnMap(routeCoords, order, routeInfo) {
    var mapWindow = getMapFrame();
    if (!mapWindow) {
      return;
    }

    var color = {
      Bike: "#22C55E",
      Van: "#3B82F6",
      Truck: "#EF4444"
    }[order.vehicleType] || "#6B7280";

    if (typeof mapWindow.drawRoute === "function") {
      mapWindow.drawRoute(routeCoords, color, order.sourceCity + " → " + order.destinationCity);
    }
    if (typeof mapWindow.updateInfoCard === "function") {
      mapWindow.updateInfoCard(
        order.sourceCity + " → " + order.destinationCity,
        routeInfo && routeInfo.distanceKm ? routeInfo.distanceKm.toFixed(1) + " km" : "-",
        routeInfo && routeInfo.durationMinutes ? routeInfo.durationMinutes.toFixed(0) + " min" : "-",
        "-",
        "-",
        "-"
      );
    }
    if (typeof mapWindow.ensureMapSize === "function") {
      mapWindow.ensureMapSize();
    }
  }

  async function fetchBestRouteGeometry(order) {
    var cacheKey = String(order.id);
    if (DELIVERY_ORDER_CACHE[cacheKey] && DELIVERY_ORDER_CACHE[cacheKey]._routeInfo) {
      return DELIVERY_ORDER_CACHE[cacheKey]._routeInfo;
    }

    var pickup = getDeliveryCityCoordinates(order.sourceCity);
    var drop = getDeliveryCityCoordinates(order.destinationCity);
    if (!pickup || !drop) {
      return null;
    }

    var routeUrl = "https://router.project-osrm.org/route/v1/driving/" + pickup.lng + "," + pickup.lat + ";" + drop.lng + "," + drop.lat + "?overview=full&geometries=geojson&steps=false";
    try {
      var response = await fetch(routeUrl);
      if (!response.ok) {
        throw new Error("Route request failed");
      }
      var data = await response.json();
      if (!data.routes || !data.routes.length) {
        throw new Error("No route data");
      }

      var route = data.routes[0];
      var coords = (route.geometry && route.geometry.coordinates ? route.geometry.coordinates : []).map(function (point) {
        return [point[1], point[0]];
      });

      var routeInfo = {
        coords: coords.length ? coords : [[pickup.lat, pickup.lng], [drop.lat, drop.lng]],
        distanceKm: (route.distance || 0) / 1000,
        durationMinutes: (route.duration || 0) / 60
      };
      DELIVERY_ORDER_CACHE[cacheKey] = DELIVERY_ORDER_CACHE[cacheKey] || order;
      DELIVERY_ORDER_CACHE[cacheKey]._routeInfo = routeInfo;
      return routeInfo;
    } catch (err) {
      return {
        coords: [[pickup.lat, pickup.lng], [drop.lat, drop.lng]],
        distanceKm: estimateDeliveryFallbackDistance(pickup, drop),
        durationMinutes: 0
      };
    }
  }

  async function selectDeliveryOrder(orderId) {
    var order = DELIVERY_ORDER_CACHE[String(orderId)];
    if (!order) {
      return;
    }

    DELIVERY_SELECTED_ORDER_ID = orderId;
    updateDeliverySelectionPanel(order);

    var routeInfo = await fetchBestRouteGeometry(order);
    if (routeInfo) {
      updateDeliverySelectionPanel(order, routeInfo);
      drawRouteOnMap(routeInfo.coords, order, routeInfo);
    }
  }

  async function acceptDeliveryOrder(orderId) {
    await selectDeliveryOrder(orderId);

    var session = readSession();
    var deliveryUsername = session && session.email ? session.email : "";
    if (!deliveryUsername) {
      alert("Please sign in as a delivery partner first.");
      return;
    }

    var response = await fetch(API_BASE + "/api/orders", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ orderId: orderId, action: "acceptOrder", deliveryUsername: deliveryUsername })
    });

    var data = await response.json();
    if (response.ok && data.success) {
      broadcastOrderChange();
      loadAndDisplayDeliveryOrders();
    } else {
      alert((data && data.message) || "Failed to accept order.");
    }
  }

  async function releaseAcceptedDeliveryOrder(orderId) {
    var session = readSession();
    var deliveryUsername = session && session.email ? session.email : "";
    if (!deliveryUsername) {
      alert("Please sign in as a delivery partner first.");
      return;
    }

    var response = await fetch(API_BASE + "/api/orders", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ orderId: orderId, action: "releaseOrder", deliveryUsername: deliveryUsername })
    });

    var data = await response.json();
    if (response.ok && data.success) {
      if (DELIVERY_SELECTED_ORDER_ID === orderId) {
        DELIVERY_SELECTED_ORDER_ID = null;
        var label = byId("deliverySelectedOrderLabel");
        var meta = byId("deliverySelectedOrderMeta");
        if (label) {
          label.textContent = "Select an order to preview the best route.";
        }
        if (meta) {
          meta.textContent = "OpenStreetMap route preview will appear on the map.";
        }
      }
      var mapWindow = getMapFrame();
      if (mapWindow && typeof mapWindow.clearRoute === "function") {
        mapWindow.clearRoute();
      }
      broadcastOrderChange();
      loadAndDisplayDeliveryOrders();
    } else {
      alert((data && data.message) || "Failed to release order.");
    }
  }

  window.selectDeliveryOrder = selectDeliveryOrder;
  window.acceptDeliveryOrder = acceptDeliveryOrder;
  window.releaseAcceptedDeliveryOrder = releaseAcceptedDeliveryOrder;

  function initAdminScreen() {
    var adminPickupCity = byId("adminPickupCity");
    var adminDropCity = byId("adminDropCity");
    var adminCities = [
      "Mumbai",
      "Pune",
      "Aurangabad",
      "Nashik",
      "Nagpur",
      "Ahmedabad",
      "Indore",
      "Bhopal"
    ];

    if (adminPickupCity && adminDropCity) {
      adminPickupCity.innerHTML = "";
      adminDropCity.innerHTML = "";

      adminCities.forEach(function (cityName) {
        var pickupOption = document.createElement("option");
        pickupOption.value = cityName;
        pickupOption.textContent = cityName;
        adminPickupCity.appendChild(pickupOption);

        var dropOption = document.createElement("option");
        dropOption.value = cityName;
        dropOption.textContent = cityName;
        adminDropCity.appendChild(dropOption);
      });

      if (adminCities.length > 1) {
        adminPickupCity.value = adminCities[0];
        adminDropCity.value = adminCities[1];
      }
    }

    var navMap = [
      ["dashboardNavButton", "dashboardPane"],
      ["ordersNavButton", "ordersPane"],
      ["crudNavButton", "crudPane"],
      ["ridersNavButton", "usersPane"],
      ["usersNavButton", "usersPane"],
      ["analyticsNavButton", "analyticsPane"],
      ["settingsNavButton", "settingsPane"]
    ].filter(function (pair) {
      return byId(pair[0]) && byId(pair[1]);
    });

    function activate(targetBtnId, targetPaneId) {
      navMap.forEach(function (pair) {
        var btn = byId(pair[0]);
        var pane = byId(pair[1]);
        if (btn) {
          btn.classList.toggle("active", pair[0] === targetBtnId);
        }
        if (pane) {
          pane.classList.toggle("hidden", pair[1] !== targetPaneId);
        }
      });
    }

    navMap.forEach(function (pair) {
      var btn = byId(pair[0]);
      if (!btn) {
        return;
      }
      btn.addEventListener("click", function () {
        activate(pair[0], pair[1]);
        if (pair[0] === "crudNavButton") {
          loadAndDisplayCrudOrders();
        }
      });
    });

    if (navMap.length > 0) {
      activate(navMap[0][0], navMap[0][1]);
    }

    var ordersTable = byId("ordersTable");
    var assignModal = byId("assignOrderModal");
    var assignOrderLabel = byId("assignOrderLabel");
    if (ordersTable && assignModal && assignOrderLabel) {
      ordersTable.addEventListener("click", function (event) {
        var target = event.target;
        if (target && target.matches("[data-open-assign]")) {
          var orderId = target.getAttribute("data-open-assign") || "";
          assignOrderLabel.textContent = "Assign order #" + orderId;
          openModal("assignOrderModal");
        }
      });
    }

    var assignOrderSubmitButton = byId("assignOrderSubmitButton");
    if (assignOrderSubmitButton) {
      assignOrderSubmitButton.addEventListener("click", function () {
        closeModal("assignOrderModal");
      });
    }

    // ========== ORDER CRUD HANDLERS ==========
    var adminCreateOrderBtn = byId("adminCreateOrderBtn");
    if (adminCreateOrderBtn) {
      adminCreateOrderBtn.addEventListener("click", function () {
        handleCreateOrder();
      });
    }

    var adminUpdateOrderBtn = byId("adminUpdateOrderBtn");
    if (adminUpdateOrderBtn) {
      adminUpdateOrderBtn.addEventListener("click", function () {
        handleUpdateOrder();
      });
    }

    var lifecycleButtons = [
      ["ordersCreatedTab", "CREATED"],
      ["ordersAcceptedTab", "ACCEPTED"],
      ["ordersOnWayTab", "ON_THE_WAY"],
      ["ordersDeliveredTab", "DELIVERED"]
    ];
    lifecycleButtons.forEach(function (pair) {
      var button = byId(pair[0]);
      if (!button) {
        return;
      }
      button.addEventListener("click", function () {
        setOrderLifecycleFilter(pair[1]);
      });
    });

    var ordersClearSortBtn = byId("ordersClearSortBtn");
    if (ordersClearSortBtn) {
      ordersClearSortBtn.addEventListener("click", function () {
        setOrderLifecycleFilter("ALL");
      });
    }

    // ========== RIDER CRUD HANDLERS ==========
    loadDashboardMetrics();
    loadAndDisplayRiders();
    loadAndDisplayOrders();  // Load orders tab on init
    loadAndDisplayCrudOrders();  // Load CRUD orders on init
    listenForOrderChanges(function () {
      loadDashboardMetrics();
      loadAndDisplayOrders();
      loadAndDisplayCrudOrders();
      loadAndDisplayRiders();
    });

    var riderCreateBtn = byId("riderCreateBtn");
    if (riderCreateBtn) {
      riderCreateBtn.addEventListener("click", function () {
        handleCreateRider();
      });
    }

    var riderClearBtn = byId("riderClearBtn");
    if (riderClearBtn) {
      riderClearBtn.addEventListener("click", function () {
        clearRiderForm();
      });
    }

    // Load orders on Orders Lifecycle tab click
    var ordersNavButton = byId("ordersNavButton");
    if (ordersNavButton) {
      ordersNavButton.addEventListener("click", function () {
        loadAndDisplayAllOrdersByStatus(ORDER_LIFECYCLE_FILTER);
      });
    }

    updateLifecycleButtonState(ORDER_LIFECYCLE_FILTER);
  }

  function handleCreateOrder() {
    var customerEmail = (byId("adminCustomerEmail") || {}).value || "";
    var pickupCity = (byId("adminPickupCity") || {}).value || "";
    var dropCity = (byId("adminDropCity") || {}).value || "";
    var vehicleType = (byId("adminVehicleType") || {}).value || "";
    var weightKg = parseFloat((byId("adminWeightKg") || {}).value || "0");
    var pickupSlot = (byId("adminPickupSlot") || {}).value || "";

    if (!customerEmail || !pickupCity || !dropCity || !vehicleType) {
      alert("Please fill in all required fields");
      return;
    }

    var payload = {
      customerEmail: customerEmail.trim(),
      pickupCity: pickupCity,
      dropCity: dropCity,
      vehicleType: vehicleType,
      weightKg: weightKg,
      pickupSlot: pickupSlot
    };

    fetch(API_BASE + "/api/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    })
      .then(function (response) {
        return response.json().then(function (data) {
          return { ok: response.ok, data: data };
        });
      })
      .then(function (result) {
        if (result.ok || result.data.success) {
          alert("Order created successfully! ID: " + result.data.orderId);
          clearOrderForm();
          loadAndDisplayOrders();
          loadAndDisplayCrudOrders();
          broadcastOrderChange();
        } else {
          alert("Error creating order: " + (result.data.message || "Unknown error"));
        }
      })
      .catch(function (err) {
        alert("Network error: " + err.message);
      });
  }

  function handleUpdateOrder() {
    var orderId = parseInt((byId("adminOrderId") || {}).value || "0");
    var pickupCity = (byId("adminPickupCity") || {}).value || "";
    var dropCity = (byId("adminDropCity") || {}).value || "";
    var vehicleType = (byId("adminVehicleType") || {}).value || "";
    var weightKg = parseFloat((byId("adminWeightKg") || {}).value || "0");
    var status = (byId("adminStatus") || {}).value || "CREATED";
    var pickupSlot = (byId("adminPickupSlot") || {}).value || "";

    if (orderId <= 0 || !pickupCity || !dropCity) {
      alert("Please enter a valid Order ID and required fields");
      return;
    }

    var payload = {
      orderId: orderId,
      action: "updateDetails",
      pickupCity: pickupCity,
      dropCity: dropCity,
      vehicleType: vehicleType,
      weightKg: weightKg,
      status: status,
      pickupSlot: pickupSlot
    };

    fetch(API_BASE + "/api/orders", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    })
      .then(function (response) {
        return response.json().then(function (data) {
          return { ok: response.ok, data: data };
        });
      })
      .then(function (result) {
        if (result.ok || result.data.success) {
          alert("Order updated successfully!");
          clearOrderForm();
          loadAndDisplayOrders();
          loadAndDisplayCrudOrders();
          broadcastOrderChange();
        } else {
          alert("Error updating order: " + (result.data.message || "Unknown error"));
        }
      })
      .catch(function (err) {
        alert("Network error: " + err.message);
      });
  }

  function handleDeleteOrder() {
    var orderId = parseInt((byId("adminOrderId") || {}).value || "0");

    if (orderId <= 0) {
      alert("Please enter a valid Order ID");
      return;
    }

    if (!confirm("Are you sure you want to delete order #" + orderId + "?")) {
      return;
    }

    fetch(API_BASE + "/api/orders?id=" + orderId, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" }
    })
      .then(function (response) {
        return response.json().then(function (data) {
          return { ok: response.ok, data: data };
        });
      })
      .then(function (result) {
        if (result.ok || result.data.success) {
          alert("Order deleted successfully!");
          clearOrderForm();
          loadAndDisplayOrders();
          loadAndDisplayCrudOrders();
          broadcastOrderChange();
        } else {
          alert("Error deleting order: " + (result.data.message || "Unknown error"));
        }
      })
      .catch(function (err) {
        alert("Network error: " + err.message);
      });
  }

  function clearOrderForm() {
    var inputs = ["adminOrderId", "adminCustomerEmail", "adminWeightKg", "adminPickupSlot"];
    inputs.forEach(function (id) {
      var el = byId(id);
      if (el) {
        el.value = "";
      }
    });
  }

  function loadAndDisplayOrders() {
    fetch(API_BASE + "/api/orders")
      .then(function (response) {
        return response.json();
      })
      .then(function (result) {
        if (result.success && result.orders) {
          renderOrderLifecycleTable(result.orders);
        }
      })
      .catch(function (err) {
        console.error("Error loading orders:", err);
      });
  }

  function populateOrderFormForEdit(id, src, dst, vehicle, status, weightKg, pickupSlot, customerEmail) {
    var orderIdField = byId("adminOrderId");
    var customerEmailField = byId("adminCustomerEmail");
    var pickupCityField = byId("adminPickupCity");
    var dropCityField = byId("adminDropCity");
    var vehicleField = byId("adminVehicleType");
    var weightField = byId("adminWeightKg");
    var pickupSlotField = byId("adminPickupSlot");
    var statusField = byId("adminStatus");

    if (orderIdField) {
      orderIdField.value = id;
    }
    if (pickupCityField) {
      pickupCityField.value = src;
    }
    if (customerEmailField && customerEmail) {
      customerEmailField.value = customerEmail;
    }
    if (dropCityField) {
      dropCityField.value = dst;
    }
    if (vehicleField) {
      vehicleField.value = vehicle;
    }
    if (weightField) {
      weightField.value = typeof weightKg === "undefined" ? "" : weightKg;
    }
    if (pickupSlotField) {
      pickupSlotField.value = pickupSlot || "";
    }
    if (statusField) {
      statusField.value = status;
    }
  }

  function loadAndDisplayCrudOrders() {
    fetch(API_BASE + "/api/orders")
      .then(function (response) {
        return response.json();
      })
      .then(function (result) {
        if (result.success && result.orders) {
          var tbody = document.querySelector("#crudOrdersTable tbody");
          if (tbody) {
            tbody.innerHTML = "";
            var sortedOrders = sortOrdersByNewestFirst(result.orders).slice(0, 10);
            sortedOrders.forEach(function (order) {
              var row = document.createElement("tr");
              var slot = order.pickupSlot || "-";
              row.style.cursor = "pointer";
              row.title = "Click to load this order into the update form";
              row.addEventListener("click", function () {
                populateOrderFormForEdit(
                  order.id,
                  order.sourceCity || "",
                  order.destinationCity || "",
                  order.vehicleType || "Bike",
                  order.status || "CREATED",
                  order.weightKg || 0,
                  order.pickupSlot || "",
                  order.customerEmail || ""
                );
              });
              row.innerHTML = "<td>" + order.id + "</td>" +
                "<td>" + (order.customerEmail || "-") + "</td>" +
                "<td>" + (order.sourceCity || "-") + " → " + (order.destinationCity || "-") + "</td>" +
                "<td>" + (order.vehicleType || "-") + "</td>" +
                "<td><strong>" + (order.status || "-") + "</strong></td>" +
                "<td>" + (order.weightKg || 0) + " kg</td>" +
                "<td>" + slot + "</td>" +
                "<td><button class='btn btn-small btn-orange' type='button' onclick=\"event.stopPropagation(); deleteOrderFromCrud(" + order.id + ")\">Delete</button></td>";
              tbody.appendChild(row);
            });
          }
        }
      })
      .catch(function (err) {
        console.error("Error loading CRUD orders:", err);
      });
  }

  function deleteOrderFromCrud(orderId) {
    if (!confirm("Are you sure you want to delete order #" + orderId + "?")) {
      return;
    }
    fetch(API_BASE + "/api/orders?id=" + orderId, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" }
    })
      .then(function (response) {
        return response.json().then(function (data) {
          return { ok: response.ok, data: data };
        });
      })
      .then(function (result) {
        if (result.ok || result.data.success) {
          alert("Order deleted successfully!");
          loadAndDisplayCrudOrders();
          loadAndDisplayOrders();
          broadcastOrderChange();
        } else {
          alert("Error deleting order: " + (result.data.message || "Unknown error"));
        }
      })
      .catch(function (err) {
        alert("Network error: " + err.message);
      });
  }

  window.deleteOrderFromCrud = deleteOrderFromCrud;

  function loadAndDisplayRiders() {
    fetch(API_BASE + "/api/riders")
      .then(function (response) {
        return response.json();
      })
      .then(function (result) {
        if (result.success && result.riders) {
          var tbody = document.querySelector("#usersTable tbody");
          if (tbody) {
            tbody.innerHTML = "";
            result.riders.forEach(function (rider) {
              var row = document.createElement("tr");
              row.innerHTML = "<td>" + rider.id + "</td>" +
                "<td>" + rider.name + "</td>" +
                "<td>" + rider.phoneNumber + "</td>" +
                "<td>" + rider.vehicleType + "</td>" +
                "<td>" + rider.availability + "</td>" +
                "<td>" + rider.currentCity + "</td>" +
                "<td><button class='btn btn-small btn-orange' onclick=\"deleteRider(" + rider.id + ")\">Delete</button></td>";
              tbody.appendChild(row);
            });
          }
        }
      })
      .catch(function (err) {
        console.error("Error loading riders:", err);
      });
  }

  function deleteRider(riderId) {
    if (!confirm("Are you sure you want to delete rider #" + riderId + "?")) {
      return;
    }

    fetch(API_BASE + "/api/riders?id=" + riderId, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" }
    })
      .then(function (response) {
        return response.json();
      })
      .then(function (result) {
        if (result.success) {
          alert("Rider deleted successfully!");
          loadAndDisplayRiders();
        } else {
          alert("Error deleting rider: " + (result.message || "Unknown error"));
        }
      })
      .catch(function (err) {
        alert("Network error: " + err.message);
      });
  }

  window.deleteRider = deleteRider;

  function handleCreateRider() {
    var name = (byId("riderName") || {}).value || "";
    var phone = (byId("riderPhone") || {}).value || "";
    var vehicleType = (byId("riderVehicleType") || {}).value || "";
    var currentCity = (byId("riderCity") || {}).value || "";

    if (!name || !phone || !vehicleType || !currentCity) {
      alert("Please fill in all required fields");
      return;
    }

    var payload = {
      name: name.trim(),
      phoneNumber: phone.trim(),
      vehicleType: vehicleType,
      currentCity: currentCity
    };

    fetch(API_BASE + "/api/riders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    })
      .then(function (response) {
        return response.json().then(function (data) {
          return { ok: response.ok, data: data };
        });
      })
      .then(function (result) {
        if (result.ok || result.data.success) {
          alert("Rider created successfully! ID: " + result.data.riderId);
          clearRiderForm();
          loadAndDisplayRiders();
        } else {
          alert("Error creating rider: " + (result.data.message || "Unknown error"));
        }
      })
      .catch(function (err) {
        alert("Network error: " + err.message);
      });
  }

  function clearRiderForm() {
    var inputs = ["riderName", "riderPhone"];
    inputs.forEach(function (id) {
      var el = byId(id);
      if (el) {
        el.value = "";
      }
    });

    var select1 = byId("riderVehicleType");
    if (select1) {
      select1.value = "Bike";
    }

    var select2 = byId("riderCity");
    if (select2) {
      select2.value = "Mumbai";
    }
  }

  function setOrderLifecycleFilter(filterName) {
    ORDER_LIFECYCLE_FILTER = normalizeLifecycleFilter(filterName);
    updateLifecycleButtonState(ORDER_LIFECYCLE_FILTER);
    loadAndDisplayOrders();
  }

  function loadAndDisplayAllOrdersByStatus(filterName) {
    if (filterName) {
      ORDER_LIFECYCLE_FILTER = normalizeLifecycleFilter(filterName);
    }
    updateLifecycleButtonState(ORDER_LIFECYCLE_FILTER);

    fetch(API_BASE + "/api/orders")
      .then(function (response) {
        return response.json();
      })
      .then(function (result) {
        if (result.success && result.orders) {
          renderOrderLifecycleTable(result.orders);
        }
      })
      .catch(function (err) {
        console.error("Error loading orders:", err);
      });
  }

  window.setOrderLifecycleFilter = setOrderLifecycleFilter;
  window.loadAndDisplayAllOrdersByStatus = loadAndDisplayAllOrdersByStatus;

  function initDeliveryScreen() {
    var bellLabel = byId("bellLabel");
    var unreadBadgeLabel = byId("unreadBadgeLabel");
    loadAndDisplayDeliveryOrders();
    listenForOrderChanges(function () {
      loadAndDisplayDeliveryOrders();
    });
    if (bellLabel && unreadBadgeLabel) {
      bellLabel.addEventListener("click", function () {
        openModal("deliveryNotificationsModal");
      });
    }

    var deliveryMarkAllReadButton = byId("deliveryMarkAllReadButton");
    if (deliveryMarkAllReadButton) {
      deliveryMarkAllReadButton.addEventListener("click", function () {
        unreadBadgeLabel.textContent = "0";
        closeModal("deliveryNotificationsModal");
      });
    }

    var mapFrame = byId("mapWebView");
    if (mapFrame) {
      mapFrame.addEventListener("load", function () {
        if (DELIVERY_SELECTED_ORDER_ID) {
          selectDeliveryOrder(DELIVERY_SELECTED_ORDER_ID);
        }
      });
    }
  }

  document.addEventListener("DOMContentLoaded", function () {
    setupPasswordVisibilityToggles();
    setupModalCloseButtons();
    if (byId("loginScreen")) {
      initLoginScreen();
    }
    if (byId("adminLoginScreen")) {
      initAdminLoginScreen();
    }
    if (byId("mainScreen")) {
      initMainScreen();
    }
    if (byId("adminScreen")) {
      initAdminScreen();
    }
    if (byId("deliveryScreen")) {
      initDeliveryScreen();
    }
  });
})();
