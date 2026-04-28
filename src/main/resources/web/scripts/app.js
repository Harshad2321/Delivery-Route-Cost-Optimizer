(function () {
  var API_BASE = "http://localhost:8081";
  var FALLBACK_USERS_KEY = "drc_fallback_accounts_v1";
  var SESSION_KEY = "drc_active_session_v1";

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

    function calculateCost() {
      var pickupCity = pickupCitySelect ? pickupCitySelect.value : "";
      var dropCity = dropCitySelect ? dropCitySelect.value : "";

      var routeEstimate = getRouteEstimate(pickupCity, dropCity);
      if (!routeEstimate) {
        if (statusLabel) {
          statusLabel.textContent = "Select different pickup and drop cities to estimate cost.";
        }
        if (costSummaryText) {
          costSummaryText.value = "Pickup and drop cities must be selected and cannot be the same.";
        }
        return;
      }

      var distanceKm = routeEstimate.distanceKm;
      var tollCost = routeEstimate.tollAmount;

      var vehicleType = byId("vehicleType") ? byId("vehicleType").value : "Bike";
      var vehicleCosts = {
        "Bike": 2,
        "Van": 3.5,
        "Truck": 5
      };

      var rate = vehicleCosts[vehicleType] || 2;
      var totalCost = (distanceKm * rate) + tollCost;

      var orderType = byId("orderType") ? byId("orderType").value : "";
      var weightValue = parseNumber(byId("weightKg") ? byId("weightKg").value : 0, 0);
      var dimensionSummary = getDimensionSummary();

      var summary = "Route: " + (pickupCity || "-") + " -> " + (dropCity || "-") + "\n"
        + "Order Type: " + (orderType || "-") + "\n"
        + "Weight: " + (weightValue || 0) + " kg\n"
        + "Dimensions: " + dimensionSummary + "\n"
        + "Vehicle: " + vehicleType + "\n"
        + "Distance (predefined): " + distanceKm.toFixed(1) + " km\n"
        + "Price per km: " + rate.toFixed(2) + "\n"
        + "Toll (predefined): " + tollCost.toFixed(2) + "\n"
        + "-------------------\n"
        + "TOTAL COST: " + totalCost.toFixed(2);

      if (costSummaryText) {
        costSummaryText.value = summary;
      }
      if (statusLabel) {
        statusLabel.textContent = "Estimate ready: " + totalCost.toFixed(2) + " INR";
      }
    }

    if (estimateCostButton) {
      estimateCostButton.addEventListener("click", calculateCost);
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
  }

  function initAdminScreen() {
    var navMap = [
      ["dashboardNavButton", "dashboardPane"],
      ["ordersNavButton", "ordersPane"],
      ["usersNavButton", "usersPane"],
      ["analyticsNavButton", "analyticsPane"],
      ["settingsNavButton", "settingsPane"]
    ];

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
      });
    });

    activate("dashboardNavButton", "dashboardPane");

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
  }

  function initDeliveryScreen() {
    var bellLabel = byId("bellLabel");
    var unreadBadgeLabel = byId("unreadBadgeLabel");
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

    var simulateMovementButton = byId("simulateMovementButton");
    var trackingStatusLabel = byId("trackingStatusLabel");
    if (simulateMovementButton && trackingStatusLabel) {
      simulateMovementButton.addEventListener("click", function () {
        trackingStatusLabel.textContent = "Manual simulation advanced by +10%";
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
