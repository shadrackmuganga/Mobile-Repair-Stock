
class StockItem {
    constructor({ id, name, category, unit, stock, minStock, costPrice, sellingPrice, condition }) {
        if (!id || !name) throw new Error('Item id and name are required');
        if (stock < 0 || minStock < 0) throw new Error('Stock values cannot be negative');

        this.id = id;
        this.name = name;
        this.category = category || 'General';
        this.unit = unit || 'piece';
        this.stock = Number(stock);
        this.minStock = Number(minStock);
        this.costPrice = Number(costPrice);
        this.sellingPrice = Number(sellingPrice);
        this.condition = condition || '';
        this.history = [];
        this.updatedAt = new Date().toISOString();
    }

    touch() {
        this.updatedAt = new Date().toISOString();
    }

    addStock(amount, reason = 'Restocked') {
        if (amount <= 0) throw new Error('Quantity must be greater than zero');
        this.stock += amount;
        this.history.push({ type: 'in', amount, reason, date: new Date().toISOString() });
        this.touch();
        return this.stock;
    }

    removeStock(amount, reason = 'Used for repair') {
        if (amount <= 0) throw new Error('Quantity must be greater than zero');
        if (amount > this.stock) throw new Error(`Not enough stock for ${this.name}`);
        this.stock -= amount;
        this.history.push({ type: 'out', amount, reason, date: new Date().toISOString() });
        this.touch();
        return this.stock;
    }

    isLowStock() {
        return this.stock <= this.minStock;
    }

    toJSON() {
        return {
            id: this.id,
            name: this.name,
            category: this.category,
            unit: this.unit,
            stock: this.stock,
            minStock: this.minStock,
            costPrice: this.costPrice,
            sellingPrice: this.sellingPrice,
            condition: this.condition,
            isLowStock: this.isLowStock(),
            history: this.history,
            updatedAt: this.updatedAt
        };
    }
}

class RepairTicket {
    constructor({ ticketId, customerName, deviceModel, itemId, quantityUsed, problem, status = 'Open' }) {
        this.ticketId = ticketId;
        this.customerName = customerName;
        this.deviceModel = deviceModel;
        this.itemId = itemId;
        this.quantityUsed = quantityUsed;
        this.problem = problem;
        this.status = status;
        this.date = new Date().toISOString();
    }
}

function sumByType(transactions, types, field) {
    const typeSet = Array.isArray(types) ? types : [types];
    return transactions
        .filter(transaction => typeSet.includes(transaction.type))
        .reduce((total, transaction) => total + (transaction[field] || 0), 0);
}

class MobileRepairStockSystem {
    constructor() {
        this.items = new Map();
        this.transactions = [];
        this.repairTickets = [];
        this.deletedItems = {};
    }

    logTransaction(entry) {
        const record = {
            txnId: `TXN-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            date: new Date().toISOString(),
            createdBy: getCurrentUserKey() || 'system',
            ...entry
        };
        this.transactions.push(record);
        return record;
    }

    addItem(itemData) {
        const item = new StockItem(itemData);
        if (this.items.has(item.id)) throw new Error(`Item with id ${item.id} already exists`);
        this.items.set(item.id, item);
        return item;
    }

    getItem(id) {
        return this.items.get(id);
    }

    deleteItem(id) {
        const item = this.getItem(id);
        if (!item) throw new Error('Item not found');

        this.items.delete(id);
        this.deletedItems[id] = new Date().toISOString();
        this.logTransaction({
            id,
            name: item.name,
            type: 'deleted',
            amount: 0,
            reason: 'Item removed from inventory',
            customer: 'Admin',
            stockBefore: item.stock,
            stockAfter: 0
        });

        this.repairTickets = this.repairTickets.filter(ticket => ticket.itemId !== id);
        return item;
    }

    listItems() {
        return [...this.items.values()].map(item => item.toJSON());
    }

    findItemsByName(searchText) {
        const term = (searchText || '').toLowerCase();
        return this.listItems().filter(item =>
            item.name.toLowerCase().includes(term) || item.id.toLowerCase().includes(term)
        );
    }

    stockIn(id, amount, reason = 'Supplier delivery') {
        const item = this.getItem(id);
        if (!item) throw new Error('Item not found');
        const stockBefore = item.stock;
        const updatedStock = item.addStock(amount, reason);
        this.logTransaction({
            id: item.id,
            name: item.name,
            type: 'in',
            amount,
            reason,
            customer: 'Supplier',
            stockBefore,
            stockAfter: updatedStock
        });
        return updatedStock;
    }

    stockOut(id, amount, reason = 'Repair used') {
        const item = this.getItem(id);
        if (!item) throw new Error('Item not found');
        const stockBefore = item.stock;
        const updatedStock = item.removeStock(amount, reason);
        this.logTransaction({
            id: item.id,
            name: item.name,
            type: 'out',
            amount,
            reason,
            customer: 'Internal',
            stockBefore,
            stockAfter: updatedStock
        });
        return updatedStock;
    }

    sellItem(id, amount, customer = 'Walk-in', reason = 'Sold to customer') {
        const item = this.getItem(id);
        if (!item) throw new Error('Item not found');
        if (amount <= 0) throw new Error('Quantity must be greater than zero');
        if (amount > item.stock) throw new Error(`Not enough stock for ${item.name}`);

        const stockBefore = item.stock;
        item.stock -= amount;
        item.touch();
        const revenue = item.sellingPrice * amount;
        const profit = (item.sellingPrice - item.costPrice) * amount;

        this.logTransaction({
            id: item.id,
            name: item.name,
            type: 'sale',
            amount,
            customer,
            reason,
            stockBefore,
            stockAfter: item.stock,
            revenue,
            profit
        });

        return { stockAfter: item.stock, revenue, profit };
    }

    createRepairTicket({ customerName, deviceModel, itemId, quantityUsed, problem }) {
        const item = this.getItem(itemId);
        if (!item) throw new Error('Item not found');
        if (quantityUsed <= 0) throw new Error('Quantity must be greater than zero');
        if (quantityUsed > item.stock) throw new Error(`Not enough stock for ${item.name}`);

        const ticketId = `RT-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
        const ticket = new RepairTicket({ ticketId, customerName, deviceModel, itemId, quantityUsed, problem });

        const stockBefore = item.stock;
        item.stock -= quantityUsed;
        item.touch();
        this.logTransaction({
            id: item.id,
            name: item.name,
            type: 'repair-use',
            amount: quantityUsed,
            customer: customerName,
            reason: `Repair ticket ${ticketId}`,
            stockBefore,
            stockAfter: item.stock
        });

        this.repairTickets.push(ticket);
        return ticket;
    }

    completeRepairTicket(ticketId) {
        const ticket = this.repairTickets.find(record => record.ticketId === ticketId);
        if (!ticket) throw new Error('Repair ticket not found');
        ticket.status = 'Completed';
        return ticket;
    }

    getOpenRepairTickets() {
        return this.repairTickets.filter(ticket => ticket.status === 'Open');
    }

    inventoryValue() {
        return [...this.items.values()].reduce((total, item) => total + item.stock * item.costPrice, 0);
    }

    totalSalesRevenue() {
        return sumByType(this.transactions, 'sale', 'revenue');
    }

    totalSalesProfit() {
        return sumByType(this.transactions, 'sale', 'profit');
    }

    totalPhoneSalesRevenue() {
        return sumByType(this.transactions, 'phone-sale', 'revenue');
    }

    totalPhoneSalesProfit() {
        return sumByType(this.transactions, 'phone-sale', 'profit');
    }

    generateLowStockReport() {
        return this.listItems().filter(item => item.isLowStock).map(item => ({
            id: item.id,
            name: item.name,
            stock: item.stock,
            minStock: item.minStock,
            status: 'Reorder required'
        }));
    }
}

const usersKey = 'mobile-repair-users';
const orgsKey = 'mobile-repair-organisations';
const sessionKey = 'mobile-repair-current-user';
const sessionOrgKey = 'mobile-repair-current-org';
const orgDataPrefix = 'mobile-repair-org-data';
const DEFAULT_PREFERENCES = { currency: 'USD', language: 'en' };

const DEFAULT_ITEMS = [
    { id: 'lcd-iphone-12', name: 'iPhone 12 LCD', category: 'Display', unit: 'piece', stock: 8, minStock: 5, costPrice: 35, sellingPrice: 60 },
    { id: 'battery-samsung-a52', name: 'Samsung A52 Battery', category: 'Battery', unit: 'piece', stock: 3, minStock: 4, costPrice: 18, sellingPrice: 32 },
    { id: 'charging-port-xiaomi-redmi', name: 'Xiaomi Redmi Charging Port', category: 'Charging', unit: 'piece', stock: 12, minStock: 6, costPrice: 12, sellingPrice: 25 }
];

const CURRENCY_FORMATS = {
    USD: { code: 'USD', locale: 'en-US' },
    EUR: { code: 'EUR', locale: 'de-DE' },
    GBP: { code: 'GBP', locale: 'en-GB' },
    KES: { code: 'KES', locale: 'en-KE' },
    UGX: { code: 'UGX', locale: 'en-UG' },
    AUD: { code: 'AUD', locale: 'en-AU' },
    NGN: { code: 'NGN', locale: 'en-NG' }
};

const UI_TEXT = {
    en: {
        settingsTitle: 'Preferences',
        currencyLabel: 'Currency',
        languageLabel: 'Language',
        saveSettings: 'Save Preferences',
        addItem: 'Add Item',
        stockActions: 'Stock Actions',
        salesRepair: 'Sales & Repair',
        quickStats: 'Quick Stats',
        salesReportFilters: 'Sales Report Filters',
        inventory: 'Inventory',
        transactions: 'Recent Transactions',
        repairTickets: 'Repair Tickets'
    },
    sw: {
        settingsTitle: 'Mapendeleo',
        currencyLabel: 'Fedha',
        languageLabel: 'Lugha',
        saveSettings: 'Hifadhi Mapendeleo',
        addItem: 'Ongeza Kitu',
        stockActions: 'Vitendo vya Hisa',
        salesRepair: 'Mauzo na Matengenezo',
        quickStats: 'Takwimu za Haraka',
        salesReportFilters: 'Vichujio vya Ripoti ya Mauzo',
        inventory: 'Hisa',
        transactions: 'Miamala ya Hivi Karibuni',
        repairTickets: 'Tiketi za Matengenezo'
    },
    es: {
        settingsTitle: 'Preferencias',
        currencyLabel: 'Moneda',
        languageLabel: 'Idioma',
        saveSettings: 'Guardar Preferencias',
        addItem: 'Agregar Artículo',
        stockActions: 'Acciones de Stock',
        salesRepair: 'Ventas y Reparaciones',
        quickStats: 'Estadísticas Rápidas',
        salesReportFilters: 'Filtros del Informe de Ventas',
        inventory: 'Inventario',
        transactions: 'Transacciones Recientes',
        repairTickets: 'Tickets de Reparación'
    },
    fr: {
        settingsTitle: 'Préférences',
        currencyLabel: 'Devise',
        languageLabel: 'Langue',
        saveSettings: 'Enregistrer les préférences',
        addItem: 'Ajouter un article',
        stockActions: 'Actions de stock',
        salesRepair: 'Ventes et réparations',
        quickStats: 'Statistiques rapides',
        salesReportFilters: 'Filtres du rapport de vente',
        inventory: 'Inventaire',
        transactions: 'Transactions récentes',
        repairTickets: 'Tickets de réparation'
    }
};

const system = new MobileRepairStockSystem();
let lastCompletedTicket = null;
let lastPhoneSale = null;
// UI labels fetched from the Java backend's /api/i18n when apiMode is on; the
// bundled UI_TEXT above is only the offline fallback.
let remoteUIText = null;

const PBKDF2_ITERATIONS = 210000; // OWASP-recommended minimum for PBKDF2-HMAC-SHA256 (2023+)
const PBKDF2_HASH_BITS = 256;

// Escapes untrusted values before they're interpolated into innerHTML/document.write
// template strings, preventing stored XSS via item names, customer names, usernames, etc.
function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, char => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[char]));
}

function bytesToHex(bytes) {
    return Array.from(bytes)
        .map(byte => byte.toString(16).padStart(2, '0'))
        .join('');
}

function hexToBytes(hex) {
    const bytes = new Uint8Array(hex.length / 2);
    for (let i = 0; i < bytes.length; i++) {
        bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
    }
    return bytes;
}

// Real, slow, salted password hashing using PBKDF2-HMAC-SHA256 via the
// browser's native Web Crypto API (no external libraries needed).
async function createPasswordHash(password, saltHex) {
    const saltBytes = hexToBytes(saltHex);
    const passwordKey = await crypto.subtle.importKey(
        'raw',
        new TextEncoder().encode(password),
        { name: 'PBKDF2' },
        false,
        ['deriveBits']
    );

    const derivedBits = await crypto.subtle.deriveBits(
        {
            name: 'PBKDF2',
            salt: saltBytes,
            iterations: PBKDF2_ITERATIONS,
            hash: 'SHA-256'
        },
        passwordKey,
        PBKDF2_HASH_BITS
    );

    return bytesToHex(new Uint8Array(derivedBits));
}

function generateSalt() {
    const saltBytes = new Uint8Array(16);
    crypto.getRandomValues(saltBytes);
    return bytesToHex(saltBytes);
}

async function normalizeUser(user) {
    if (!user) return null;
    if (user.passwordHash && user.passwordSalt) return user;
    if (user.password) {
        const salt = generateSalt();
        return {
            ...user,
            passwordSalt: salt,
            passwordHash: await createPasswordHash(user.password, salt),
            password: undefined
        };
    }
    return user;
}

// Cloud sync: when firebase-config.js has been filled in with a real
// Firestore project, every device reading this page shares the same live
// data (users, organisations, inventory) instead of each browser having its
// own isolated localStorage copy. Until it's configured — or if it fails to
// load for any reason — everything below falls back to localStorage so the
// app keeps working as a single-device demo.
let firestoreDb = null;
let firebaseAuth = null;
try {
    if (typeof firebase !== 'undefined' && typeof firebaseConfig !== 'undefined' &&
        firebaseConfig.apiKey && firebaseConfig.apiKey !== 'YOUR_API_KEY') {
        firebase.initializeApp(firebaseConfig);
        firestoreDb = firebase.firestore();
        if (typeof firebase.auth === 'function') firebaseAuth = firebase.auth();
    }
} catch (err) {
    console.warn('Firebase init failed; using local-only storage.', err);
}

// ---- Java backend REST adapter --------------------------------------------
// When this page is served by the Spring Boot backend (rather than the
// standalone static server), the backend exposes a Firestore-shaped document
// API at /api/db/{collection}/{id}. Detection is automatic: if /api/health
// answers, every read/write below goes through the backend instead of talking
// to Firestore (or localStorage) directly from the browser.
let apiMode = false;

async function detectApi() {
    try {
        const response = await fetch('/api/health', { headers: { Accept: 'application/json' } });
        if (response.ok) {
            const body = await response.json();
            apiMode = Boolean(body && body.status === 'ok');
        }
    } catch {
        apiMode = false;
    }
    return apiMode;
}

// True when organisation/user/inventory data lives somewhere shared: either
// Firestore (browser SDK) or the Java backend REST API.
function remoteActive() {
    return Boolean(firestoreDb) || apiMode;
}

async function apiGetDoc(collection, id) {
    const response = await fetch(`/api/db/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`, {
        headers: { Accept: 'application/json' }
    });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`GET ${collection}/${id} → ${response.status}`);
    return response.json();
}

async function apiSetDoc(collection, id, payload) {
    const response = await fetch(`/api/db/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    return response.ok;
}

async function apiListCollection(collection) {
    const response = await fetch(`/api/db/${encodeURIComponent(collection)}`, {
        headers: { Accept: 'application/json' }
    });
    if (!response.ok) throw new Error(`GET ${collection} → ${response.status}`);
    return response.json();
}

function cloudSyncActive() {
    return remoteActive();
}

// Once Firebase Auth is wired in, Google's servers verify every password —
// this app's JavaScript never sees or checks a real password itself, and
// Firestore security rules can trust request.auth.uid as a real identity.
function cloudAuthActive() {
    return Boolean(firebaseAuth);
}

function describeAuthError(err) {
    const messages = {
        'auth/email-already-in-use': 'That email is already registered — try logging in instead.',
        'auth/invalid-email': "That doesn't look like a valid email address.",
        'auth/weak-password': 'Password must be at least 6 characters.',
        'auth/user-not-found': 'No account found with that email.',
        'auth/wrong-password': 'Incorrect email or password.',
        'auth/invalid-credential': 'Incorrect email or password.',
        'auth/requires-recent-login': 'For security, please log out and back in before changing your password.',
        'auth/too-many-requests': 'Too many attempts — please wait a bit and try again.'
    };
    return (err && messages[err.code]) || (err && err.message) || 'Something went wrong — please try again.';
}

// Calls the Java backend's organisation business logic (inventory, sales,
// phone trades, repairs, preferences, reset). The server performs the
// validation/stock maths, persists the authoritative orgData document, and
// broadcasts it over SSE; we apply the returned state so the UI re-renders
// without any client-side rules. Only used when apiMode is on.
async function orgApi(path, method = 'POST', body) {
    const orgId = getCurrentOrgId();
    if (!orgId) throw new Error('No organisation is loaded.');
    const response = await fetch(`/api/org/${encodeURIComponent(orgId)}${path}`, {
        method,
        headers: { 'Content-Type': 'application/json', 'X-Org-User': getCurrentUserKey() || '' },
        body: body !== undefined ? JSON.stringify(body) : undefined
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(data.message || data.error || `Request failed (${response.status})`);
    }
    if (data.state) applyOrgData(data.state);
    return data.result;
}

// Downloads a server-generated report (csv/xlsx/json) from the Java backend.
// Used instead of the client-side SheetJS/Blob exports when apiMode is on.
async function orgDownload(path, filename) {
    const orgId = getCurrentOrgId();
    if (!orgId) throw new Error('No organisation is loaded.');
    const response = await fetch(`/api/org/${encodeURIComponent(orgId)}${path}`, {
        headers: { 'X-Org-User': getCurrentUserKey() || '' }
    });
    if (!response.ok) throw new Error(`Export failed (${response.status})`);
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

// Fetches every UI label set from the backend so translations no longer ship
// inside app.js. Falls back to the bundled UI_TEXT if the call fails.
async function loadRemoteI18n() {
    try {
        const response = await fetch('/api/i18n', { headers: { Accept: 'application/json' } });
        if (response.ok) {
            const body = await response.json();
            if (body && typeof body === 'object') remoteUIText = body;
        }
    } catch {
        remoteUIText = null;
    }
}

async function fsGetDoc(collectionName, id) {
    try {
        if (apiMode) return await apiGetDoc(collectionName, id);
        if (firestoreDb) {
            const snap = await firestoreDb.collection(collectionName).doc(id).get();
            return snap.exists ? snap.data() : null;
        }
    } catch (err) {
        console.error('Document read failed:', err);
    }
    return null;
}

async function fsSetDoc(collectionName, id, data) {
    try {
        if (apiMode) return await apiSetDoc(collectionName, id, data);
        if (firestoreDb) {
            await firestoreDb.collection(collectionName).doc(id).set(data);
            return true;
        }
    } catch (err) {
        console.error('Document write failed:', err);
    }
    return false;
}

async function fsListCollection(collectionName) {
    try {
        if (apiMode) return await apiListCollection(collectionName);
        if (firestoreDb) {
            const snap = await firestoreDb.collection(collectionName).get();
            return snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
        }
    } catch (err) {
        console.error('Collection list failed:', err);
    }
    return [];
}

async function getUsers() {
    if (remoteActive()) {
        return (await fsListCollection('users')).map(row => ({ username: row.email || row.id, uid: row.id, ...row }));
    }
    try {
        const rawUsers = JSON.parse(localStorage.getItem(usersKey) || '[]');
        let upgraded = false;
        const normalized = await Promise.all(rawUsers.map(async user => {
            const result = await normalizeUser(user);
            if (result !== user) upgraded = true;
            return result;
        }));
        const users = normalized.filter(Boolean);
        // Persist the hashed form so a legacy plaintext password is only
        // upgraded (expensive PBKDF2) once, instead of on every single read.
        if (upgraded) {
            try {
                localStorage.setItem(usersKey, JSON.stringify(users));
            } catch {
                /* quota/private-mode errors: the in-memory result is still valid */
            }
        }
        return users;
    } catch {
        return [];
    }
}

async function upsertUser(user) {
    const record = {
        fullName: user.fullName,
        contact: user.contact || '',
        role: user.role || 'staff',
        orgId: user.orgId,
        passwordHash: user.passwordHash,
        passwordSalt: user.passwordSalt
    };
    if (remoteActive()) return fsSetDoc('users', user.username, record);
    try {
        const rawUsers = JSON.parse(localStorage.getItem(usersKey) || '[]');
        const idx = rawUsers.findIndex(item => item.username === user.username);
        const entry = { username: user.username, ...record };
        if (idx >= 0) rawUsers[idx] = entry; else rawUsers.push(entry);
        localStorage.setItem(usersKey, JSON.stringify(rawUsers));
        return true;
    } catch {
        return false;
    }
}

// Writes a user's profile (name/role/org — never a password; Firebase Auth
// holds that) keyed by their Firebase Auth uid.
async function upsertUserProfile(uid, profile) {
    return fsSetDoc('users', uid, profile);
}

// Cached at login so synchronous code (like logging a transaction) can show
// a readable name instead of an opaque uid without awaiting storage.
let currentUserDisplayName = null;

function getCurrentUserKey() {
    return currentUserDisplayName || localStorage.getItem(sessionKey) || null;
}

function getCurrentOrgId() {
    return localStorage.getItem(sessionOrgKey) || null;
}

function setCurrentOrg(orgId) {
    if (orgId) localStorage.setItem(sessionOrgKey, orgId);
}

function getOrgDataKey(orgId = getCurrentOrgId()) {
    return orgId ? `${orgDataPrefix}:${orgId}` : null;
}

function slugify(text) {
    return String(text || '').toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}

async function getOrganisationName(orgId) {
    if (remoteActive()) {
        const data = await fsGetDoc('organisations', orgId);
        return data ? (data.orgName || orgId) : orgId;
    }
    try {
        const orgs = JSON.parse(localStorage.getItem(orgsKey) || '[]');
        const org = orgs.find(item => item.orgId === orgId);
        return org ? org.orgName : orgId;
    } catch {
        return orgId;
    }
}

// Read-only check: does this organisation already exist? Used to tell whether
// a signup will create a new org (and become its admin) or join an existing
// one as staff.
async function organisationExists(orgName) {
    const orgId = slugify(orgName);
    if (remoteActive()) return Boolean(await fsGetDoc('organisations', orgId));
    const orgs = JSON.parse(localStorage.getItem(orgsKey) || '[]');
    return orgs.some(org => org.orgId === orgId);
}

async function findOrCreateOrganisation(orgName) {
    const orgId = slugify(orgName);
    if (remoteActive()) {
        const existing = await fsGetDoc('organisations', orgId);
        if (existing) return { org: { orgId, ...existing }, created: false };
        const org = { orgName: orgName.trim() };
        await fsSetDoc('organisations', orgId, org);
        return { org: { orgId, ...org }, created: true };
    }
    const orgs = JSON.parse(localStorage.getItem(orgsKey) || '[]');
    const existing = orgs.find(org => org.orgId === orgId);
    if (existing) return { org: existing, created: false };

    const org = { orgId, orgName: orgName.trim() };
    orgs.push(org);
    localStorage.setItem(orgsKey, JSON.stringify(orgs));
    return { org, created: true };
}

// Loads one organisation's shared inventory/transactions/tickets/preferences
// document, or null if it doesn't exist yet.
async function storeGetOrgData(orgId) {
    if (remoteActive()) return fsGetDoc('orgData', orgId);
    try {
        const raw = localStorage.getItem(getOrgDataKey(orgId));
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}

async function storeSetOrgData(orgId, payload) {
    if (remoteActive()) return fsSetDoc('orgData', orgId, payload);
    try {
        localStorage.setItem(getOrgDataKey(orgId), JSON.stringify(payload));
        return true;
    } catch {
        return false;
    }
}

// ---- Conflict-free merging -------------------------------------------------
// Saving the whole organisation document in one write means two devices that
// save at the same time would clobber each other (last write wins, the other
// device's sales vanish). Instead of blindly overwriting, merge the incoming
// state with whatever is currently stored:
//   - transactions are append-only and de-duplicated by txnId
//   - repair tickets are keyed by ticketId (incoming wins, so status updates stick)
//   - items are merged per-id, preferring the most recently updated copy
//   - deletions are tracked as tombstones so a merge never resurrects an item
function mergeItemCollections(existing, incoming) {
    const byId = new Map();
    const upsert = item => {
        if (!item || !item.id) return;
        const current = byId.get(item.id);
        if (!current || (current.updatedAt || '') <= (item.updatedAt || '')) byId.set(item.id, item);
    };
    (existing || []).forEach(upsert);
    (incoming || []).forEach(upsert);
    return [...byId.values()];
}

function dropDeletedItems(items, tombstones) {
    return items.filter(item => {
        const deletedAt = tombstones[item.id];
        return !deletedAt || deletedAt < (item.updatedAt || '');
    });
}

function mergeTransactions(existing, incoming) {
    const byKey = new Map();
    const keyOf = txn => txn.txnId || [txn.type, txn.id, txn.date, txn.amount, txn.customer].join('|');
    (existing || []).forEach(txn => txn && byKey.set(keyOf(txn), txn));
    (incoming || []).forEach(txn => txn && byKey.set(keyOf(txn), txn));
    return [...byKey.values()].sort((a, b) => new Date(a.date) - new Date(b.date));
}

function mergeRepairTickets(existing, incoming) {
    const byId = new Map();
    const upsert = ticket => {
        if (!ticket || !ticket.ticketId) return;
        const current = byId.get(ticket.ticketId);
        // Never let a stale device regress a completed ticket back to open.
        if (current && current.status === 'Completed') return;
        byId.set(ticket.ticketId, ticket);
    };
    (existing || []).forEach(upsert);
    (incoming || []).forEach(upsert);
    return [...byId.values()];
}

function mergeOrgDataPayload(existing, incoming) {
    const base = existing || {};
    const inc = incoming || {};
    const deletedItems = { ...(base.deletedItems || {}), ...(inc.deletedItems || {}) };
    const items = dropDeletedItems(mergeItemCollections(base.items, inc.items), deletedItems);
    return {
        items,
        transactions: mergeTransactions(base.transactions, inc.transactions),
        repairTickets: mergeRepairTickets(base.repairTickets, inc.repairTickets),
        deletedItems,
        preferences: { ...DEFAULT_PREFERENCES, ...(base.preferences || {}), ...(inc.preferences || {}) },
        updatedAt: new Date().toISOString()
    };
}

// Read-modify-write against the latest stored document. In Firestore this runs
// inside an atomic transaction so two concurrent saves are serialised; in
// localStorage mode it prevents two tabs from overwriting each other's changes.
async function mergeOrgData(orgId, payload) {
    if (!orgId) return;
    if (!firestoreDb) {
        const existing = await storeGetOrgData(orgId);
        return storeSetOrgData(orgId, mergeOrgDataPayload(existing, payload));
    }
    const ref = firestoreDb.collection('orgData').doc(orgId);
    try {
        await firestoreDb.runTransaction(async txn => {
            const snap = await txn.get(ref);
            txn.set(ref, mergeOrgDataPayload(snap.exists ? snap.data() : null, payload));
        });
        return true;
    } catch (err) {
        console.error('Firestore merge transaction failed; falling back to a direct write.', err);
        return storeSetOrgData(orgId, payload);
    }
}

async function seedOrgData(orgId) {
    if (!orgId || (await storeGetOrgData(orgId))) return;
    await storeSetOrgData(orgId, {
        items: DEFAULT_ITEMS.map(item => ({ ...item })),
        transactions: [],
        repairTickets: [],
        deletedItems: {},
        preferences: { ...DEFAULT_PREFERENCES },
        updatedAt: new Date().toISOString()
    });
}

// In-memory cache of the current org's preferences, kept in sync with
// loadOrgState/saveOrgState so synchronous renderers like formatMoney can
// read it without awaiting storage on every table cell.
let currentPreferences = { ...DEFAULT_PREFERENCES };

// Keeps every open device in sync live: when Firestore is configured, any
// write (e.g. admin stocking an item on their laptop) pushes an update here
// on every other device that has this organisation open, without needing a
// manual refresh.
let orgDataUnsubscribe = null;
function subscribeToOrgData(orgId) {
    if (orgDataUnsubscribe) {
        orgDataUnsubscribe();
        orgDataUnsubscribe = null;
    }
    if (!orgId || !remoteActive()) return;

    if (apiMode) {
        // Java backend: live updates arrive over Server-Sent Events.
        const source = new EventSource(`/api/db/orgData/${encodeURIComponent(orgId)}/stream`);
        source.addEventListener('update', async event => {
            try {
                applyOrgData(JSON.parse(event.data));
                await populateReportUserFilter();
                refreshUI();
            } catch (err) {
                console.error('Live sync error:', err);
            }
        });
        orgDataUnsubscribe = () => source.close();
        return;
    }

    orgDataUnsubscribe = firestoreDb.collection('orgData').doc(orgId).onSnapshot(async snap => {
        if (!snap.exists) return;
        applyOrgData(snap.data());
        await populateReportUserFilter();
        refreshUI();
    });
}

function applyOrgData(data) {
    const tombstones = (data.deletedItems && typeof data.deletedItems === 'object') ? data.deletedItems : {};
    system.items = new Map();
    system.transactions = Array.isArray(data.transactions) ? data.transactions : [];
    system.repairTickets = Array.isArray(data.repairTickets) ? data.repairTickets : [];
    system.deletedItems = tombstones;
    (Array.isArray(data.items) ? data.items : []).forEach(item => {
        if (!item || !item.id) return;
        if (system.items.has(item.id)) return;
        if (tombstones[item.id] && tombstones[item.id] >= (item.updatedAt || '')) return;
        try {
            const created = system.addItem(item);
            if (Array.isArray(item.history)) created.history = item.history;
            created.updatedAt = typeof item.updatedAt === 'string' ? item.updatedAt : '';
        } catch (err) {
            console.warn(`Skipping invalid inventory item "${item.id}":`, err.message);
        }
    });
    currentPreferences = { ...DEFAULT_PREFERENCES, ...(data.preferences || {}) };
}

// Loads the shared inventory/transactions/tickets for one organisation into
// the in-memory `system`, seeding default data on first use.
async function loadOrgState(orgId = getCurrentOrgId()) {
    system.items = new Map();
    system.transactions = [];
    system.repairTickets = [];
    system.deletedItems = {};
    currentPreferences = { ...DEFAULT_PREFERENCES };
    if (!orgId) return;

    await seedOrgData(orgId);
    const data = await storeGetOrgData(orgId);
    if (data) applyOrgData(data);
    subscribeToOrgData(orgId);
}

async function saveOrgState(orgId = getCurrentOrgId()) {
    if (!orgId) return;
    await mergeOrgData(orgId, {
        items: system.listItems(),
        transactions: system.transactions,
        repairTickets: system.repairTickets,
        deletedItems: system.deletedItems,
        preferences: currentPreferences
    });
}

async function ensureDefaultAccount() {
    if (firebaseAuth) return; // real accounts: the first signup creates their org and becomes its admin
    const { org } = await findOrCreateOrganisation('Demo Repair Shop');
    const users = await getUsers();

    if (!users.some(user => user.username === 'admin')) {
        const adminSalt = generateSalt();
        await upsertUser({
            username: 'admin',
            fullName: 'Administrator',
            contact: 'admin@demo.repair',
            role: 'admin',
            orgId: org.orgId,
            passwordSalt: adminSalt,
            passwordHash: await createPasswordHash('admin123', adminSalt)
        });
    }

    if (!users.some(user => user.username === 'staff')) {
        const staffSalt = generateSalt();
        await upsertUser({
            username: 'staff',
            fullName: 'Staff Member',
            contact: 'staff@demo.repair',
            role: 'staff',
            orgId: org.orgId,
            passwordSalt: staffSalt,
            passwordHash: await createPasswordHash('staff123', staffSalt)
        });
    }

    // Backfill role/org on any legacy accounts missing them.
    await Promise.all(users
        .filter(user => !user.role || !user.orgId)
        .map(user => upsertUser({
            ...user,
            role: user.role || (user.username === 'admin' ? 'admin' : 'staff'),
            orgId: user.orgId || org.orgId
        })));

    await seedOrgData(org.orgId);
}

async function getCurrentUserProfile() {
    const key = localStorage.getItem(sessionKey);
    if (!key) return null;
    if (firebaseAuth) {
        const data = await fsGetDoc('users', key);
        return data ? { username: data.email, uid: key, ...data } : null;
    }
    const users = await getUsers();
    return users.find(user => user.username === key) || null;
}

function isLoggedIn() {
    return Boolean(localStorage.getItem(sessionKey));
}

async function isAdminUser() {
    const user = await getCurrentUserProfile();
    return Boolean(user && user.role === 'admin');
}

function setCurrentUser(username) {
    localStorage.setItem(sessionKey, username);
}

function clearCurrentUser() {
    localStorage.removeItem(sessionKey);
    localStorage.removeItem(sessionOrgKey);
}

function showAuthScreen(mode = 'login') {
    const authScreen = document.getElementById('auth-screen');
    const appShell = document.getElementById('app-shell');
    const loginForm = document.getElementById('login-form');
    const signupForm = document.getElementById('signup-form');
    const toggleText = document.getElementById('auth-message');
    const toggleButton = document.getElementById('toggle-auth-mode');

    if (mode === 'signup') {
        loginForm.classList.add('hidden');
        signupForm.classList.remove('hidden');
        toggleText.textContent = 'Already have an account?';
        toggleButton.textContent = 'Login';
    } else {
        loginForm.classList.remove('hidden');
        signupForm.classList.add('hidden');
        toggleText.textContent = 'Need an account?';
        toggleButton.textContent = 'Create one';
    }

    authScreen.classList.remove('hidden');
    appShell.classList.add('hidden');
}

async function buildUserBadgeText(user) {
    if (!user) return 'Guest';
    const org = await getOrganisationName(user.orgId);
    return `${user.fullName || user.username} · ${org} (${user.role || 'staff'})`;
}

async function showDashboard() {
    const authScreen = document.getElementById('auth-screen');
    const appShell = document.getElementById('app-shell');
    authScreen.classList.add('hidden');
    appShell.classList.remove('hidden');
    const badge = document.getElementById('current-user-badge');
    if (badge) badge.textContent = await buildUserBadgeText(await getCurrentUserProfile());
    // Rebuild the staff filter now that we know who's logged in.
    await populateReportUserFilter();
}

// Compares two equal-length hex digests without short-circuiting on the first
// differing character, so the time taken doesn't leak how much of the hash
// matched (a timing side-channel on the password check).
function constantTimeEqual(a, b) {
    if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length) return false;
    let mismatch = 0;
    for (let i = 0; i < a.length; i++) {
        mismatch |= a.charCodeAt(i) ^ b.charCodeAt(i);
    }
    return mismatch === 0;
}

async function verifyUserPassword(candidatePassword, storedUser) {
    if (!storedUser || !storedUser.passwordSalt || !storedUser.passwordHash) return false;
    const candidateHash = await createPasswordHash(candidatePassword, storedUser.passwordSalt);
    return constantTimeEqual(candidateHash, storedUser.passwordHash);
}

async function handleLoginSubmit(event) {
    event.preventDefault();
    const identifier = document.getElementById('login-username').value.trim();
    const password = document.getElementById('login-password').value;

    if (firebaseAuth) {
        try {
            const cred = await firebaseAuth.signInWithEmailAndPassword(identifier, password);
            const profile = await fsGetDoc('users', cred.user.uid);
            if (!profile) {
                alert('Signed in, but no profile was found for this account — contact your admin.');
                await firebaseAuth.signOut();
                return;
            }
            currentUserDisplayName = profile.email;
            setCurrentUser(cred.user.uid);
            setCurrentOrg(profile.orgId);
            await loadOrgState();
            document.getElementById('login-form').reset();
            await showDashboard();
            refreshUI();
        } catch (err) {
            alert(describeAuthError(err));
        }
        return;
    }

    const users = await getUsers();
    const user = users.find(item => item.username === identifier);

    if (!user || !(await verifyUserPassword(password, user))) {
        alert('Invalid username or password');
        return;
    }

    if (!user.role) {
        user.role = user.username === 'admin' ? 'admin' : 'staff';
        await upsertUser(user);
    }

    currentUserDisplayName = user.username;
    setCurrentUser(user.username);
    setCurrentOrg(user.orgId);
    await loadOrgState();
    document.getElementById('login-form').reset();
    await showDashboard();
    refreshUI();
}

async function handleSignupSubmit(event) {
    event.preventDefault();
    const fullName = document.getElementById('signup-name').value.trim();
    const orgName = document.getElementById('signup-org').value.trim();
    const identifier = document.getElementById('signup-username').value.trim();
    const contact = document.getElementById('signup-contact').value.trim();
    const password = document.getElementById('signup-password').value;
    const role = document.getElementById('signup-role').value;

    if (!fullName || !orgName || !identifier || !password) {
        alert('Please fill in all fields, including your organisation name');
        return;
    }

    if (role === 'staff' && (await organisationExists(orgName)) && !contact) {
        alert('A contact (email or phone) is required for staff accounts.');
        return;
    }

    if (firebaseAuth) {
        try {
            const { org, created } = await findOrCreateOrganisation(orgName);
            const cred = await firebaseAuth.createUserWithEmailAndPassword(identifier, password);
            const saved = await upsertUserProfile(cred.user.uid, {
                email: identifier,
                fullName,
                contact,
                role: created ? 'admin' : role,
                orgId: org.orgId
            });
            if (created) await seedOrgData(org.orgId);
            await firebaseAuth.signOut();
            if (!saved) {
                alert('Account created, but saving your profile failed — contact your admin.');
                return;
            }
            document.getElementById('signup-form').reset();
            showAuthScreen('login');
            alert(created
                ? `Organisation "${org.orgName}" created. You're the admin — please login.`
                : `Joined "${org.orgName}". Please login.`);
        } catch (err) {
            alert(describeAuthError(err));
        }
        return;
    }

    const users = await getUsers();
    if (users.some(user => user.username.toLowerCase() === identifier.toLowerCase())) {
        alert('That username already exists');
        return;
    }

    const { org, created } = await findOrCreateOrganisation(orgName);
    const salt = generateSalt();
    const saved = await upsertUser({
        username: identifier,
        fullName,
        contact,
        role: created ? 'admin' : role,
        orgId: org.orgId,
        passwordSalt: salt,
        passwordHash: await createPasswordHash(password, salt)
    });
    if (!saved) {
        alert('Could not create the account — please try again.');
        return;
    }
    await seedOrgData(org.orgId);

    document.getElementById('signup-form').reset();
    showAuthScreen('login');
    alert(created
        ? `Organisation "${org.orgName}" created. You're the admin — please login.`
        : `Joined "${org.orgName}". Please login.`);
}

// In Firebase-Auth mode, confirms with Firebase itself whether this browser
// still has a valid signed-in session (it persists this independently of
// our own localStorage flag) and re-loads the profile if so.
async function resolveSession() {
    if (!firebaseAuth) return isLoggedIn();
    return new Promise(resolve => {
        const unsubscribe = firebaseAuth.onAuthStateChanged(async (user) => {
            unsubscribe();
            if (!user) {
                clearCurrentUser();
                resolve(false);
                return;
            }
            const profile = await fsGetDoc('users', user.uid);
            if (!profile) {
                resolve(false);
                return;
            }
            currentUserDisplayName = profile.email;
            setCurrentUser(user.uid);
            setCurrentOrg(profile.orgId);
            resolve(true);
        });
    });
}

async function handleLogout() {
    if (orgDataUnsubscribe) {
        orgDataUnsubscribe();
        orgDataUnsubscribe = null;
    }
    currentUserDisplayName = null;
    clearCurrentUser();
    if (firebaseAuth) {
        try { await firebaseAuth.signOut(); } catch { /* ignore */ }
    }
    const badge = document.getElementById('current-user-badge');
    if (badge) badge.textContent = 'Guest';
    showAuthScreen('login');
}

function populateCategoryFilter() {
    const select = document.getElementById('category-filter');
    const categories = [...new Set(system.listItems().map(item => item.category))].sort();

    const currentValue = select.value || 'all';
    select.innerHTML = '<option value="all">All categories</option>' +
        categories.map(category => `<option value="${escapeHtml(category)}">${escapeHtml(category)}</option>`).join('');
    select.value = categories.includes(currentValue) ? currentValue : 'all';
}

function populateItemIdOptions() {
    const datalist = document.getElementById('item-id-options');
    if (!datalist) return;

    datalist.innerHTML = system.listItems().map(item =>
        `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)} - ${item.stock} in stock</option>`
    ).join('');
}

function getVisibleItems() {
    const searchTerm = document.getElementById('search-box').value;
    const category = document.getElementById('category-filter').value;

    return system.findItemsByName(searchTerm).filter(item => {
        return category === 'all' || item.category === category;
    });
}

// Query string for the server-side inventory export endpoints, mirroring the
// current search + category filters.
function inventoryQuery() {
    const q = document.getElementById('search-box')?.value || '';
    const category = document.getElementById('category-filter')?.value || 'all';
    return `?q=${encodeURIComponent(q)}&category=${encodeURIComponent(category)}`;
}

function formatMoney(value) {
    const { currency } = currentPreferences;
    const config = CURRENCY_FORMATS[currency] || CURRENCY_FORMATS.USD;
    return new Intl.NumberFormat(config.locale, {
        style: 'currency',
        currency: config.code,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(Number(value) || 0);
}

function populateCurrencyOptions() {
    const options = Object.keys(CURRENCY_FORMATS)
        .map(code => `<option value="${code}">${code}</option>`)
        .join('');
    ['settings-currency', 'topbar-currency'].forEach(id => {
        const select = document.getElementById(id);
        if (select && !select.options.length) select.innerHTML = options;
    });
}

function applyUserPreferences() {
    const prefs = currentPreferences;
    populateCurrencyOptions();

    ['settings-currency', 'topbar-currency'].forEach(id => {
        const control = document.getElementById(id);
        if (control) control.value = prefs.currency;
    });

    const languageControl = document.getElementById('settings-language');
    if (languageControl) languageControl.value = prefs.language;

    const labels = (remoteUIText && remoteUIText[prefs.language]) || UI_TEXT[prefs.language] || UI_TEXT.en;
    document.querySelectorAll('[data-i18n]').forEach(element => {
        const key = element.dataset.i18n;
        if (labels[key]) {
            element.textContent = labels[key];
        }
    });
}

function renderInventory(items = system.listItems()) {
    const tbody = document.getElementById('inventory-table');
    tbody.innerHTML = items.map(item => `
    <tr>
      <td>${escapeHtml(item.id)}</td>
      <td>${escapeHtml(item.name)}</td>
      <td>${escapeHtml(item.category)}</td>
      <td>${item.stock}</td>
      <td>${item.minStock}</td>
      <td>${formatMoney(item.costPrice)}</td>
      <td>${formatMoney(item.sellingPrice)}</td>
      <td class="${item.isLowStock ? 'status-low' : 'status-ok'}">${item.isLowStock ? 'Low' : 'OK'}</td>
    </tr>
  `).join('') || '<tr><td colspan="8">No items found.</td></tr>';
}

function renderPhoneInventory() {
    const tbody = document.getElementById('phone-stock-table');
    if (!tbody) return;

    const phones = system.listItems().filter(item => item.category === 'Phone');
    tbody.innerHTML = phones.map(item => {
        const [brand = '', ...rest] = (item.name || '').split(' ');
        const model = rest.join(' ') || 'Unknown';
        return `
            <tr>
              <td>${escapeHtml(brand)}</td>
              <td>${escapeHtml(model)}</td>
              <td>${escapeHtml(item.condition || 'Grade A')}</td>
              <td>${item.stock}</td>
              <td>${formatMoney(item.costPrice)}</td>
              <td>${formatMoney(item.sellingPrice)}</td>
            </tr>
        `;
    }).join('') || '<tr><td colspan="6">No phone stock yet.</td></tr>';
}

async function populateReportUserFilter() {
    const select = document.getElementById('report-user-filter');
    if (!select) return;

    const orgId = getCurrentOrgId();
    const users = (await getUsers()).filter(user => user.orgId === orgId).map(user => user.username);
    const current = select.value || 'all';
    const uniqueUsers = [...new Set(['all', ...users, ...system.transactions.map(txn => txn.createdBy || 'system')])]
        .filter(Boolean);

    select.innerHTML = uniqueUsers.map(user => `<option value="${escapeHtml(user)}">${user === 'all' ? 'All staff' : escapeHtml(user)}</option>`).join('');
    if (uniqueUsers.includes(current)) {
        select.value = current;
    } else {
        select.value = 'all';
    }
}

function filterTransactions() {
    const startDate = document.getElementById('report-start-date')?.value;
    const endDate = document.getElementById('report-end-date')?.value;
    const selectedUser = document.getElementById('report-user-filter')?.value || 'all';
    const reportType = document.getElementById('report-type-filter')?.value || 'all';

    return system.transactions.filter(txn => {
        const date = new Date(txn.date);
        if (startDate && date < new Date(`${startDate}T00:00:00`)) return false;
        if (endDate && date > new Date(`${endDate}T23:59:59`)) return false;
        if (selectedUser !== 'all' && (txn.createdBy || 'system') !== selectedUser) return false;
        if (reportType !== 'all' && txn.type !== reportType) return false;
        return true;
    });
}

function getFilteredTransactions() {
    return filterTransactions().slice(-10).reverse();
}

function renderTransactions() {
    const tbody = document.getElementById('transactions-table');
    const rows = getFilteredTransactions();
    tbody.innerHTML = rows.map(txn => `
    <tr>
      <td>${escapeHtml(txn.type)}</td>
      <td>${escapeHtml(txn.name)}</td>
      <td>${txn.amount || 0}</td>
      <td>${escapeHtml(txn.customer || '')}</td>
      <td>${new Date(txn.date).toLocaleString()}</td>
    </tr>
  `).join('') || '<tr><td colspan="5">No transactions yet.</td></tr>';
}

function renderRepairTickets() {
    const tbody = document.getElementById('repair-table');
    tbody.innerHTML = system.repairTickets.slice(-10).reverse().map(ticket => `
    <tr>
      <td>${escapeHtml(ticket.ticketId)}</td>
      <td>${escapeHtml(ticket.customerName)}</td>
      <td>${escapeHtml(ticket.deviceModel)}</td>
      <td>${escapeHtml(ticket.itemId)}</td>
      <td>${ticket.quantityUsed}</td>
      <td>${escapeHtml(ticket.status)}</td>
    </tr>
  `).join('') || '<tr><td colspan="6">No repair tickets yet.</td></tr>';
}

function renderStats() {
    const allItems = system.listItems();
    const phoneItems = allItems.filter(item => item.category === 'Phone');
    const phoneStockCount = phoneItems.reduce((total, item) => total + Number(item.stock || 0), 0);
    const phoneInventoryValue = phoneItems.reduce((total, item) => total + (Number(item.stock || 0) * Number(item.costPrice || 0)), 0);

    document.getElementById('total-items').textContent = String(allItems.length);
    document.getElementById('total-inventory').textContent = formatMoney(system.inventoryValue());
    document.getElementById('sales-revenue').textContent = formatMoney(system.totalSalesRevenue());
    document.getElementById('sales-profit').textContent = formatMoney(system.totalSalesProfit());
    document.getElementById('phone-sales-revenue').textContent = formatMoney(system.totalPhoneSalesRevenue());
    document.getElementById('phone-sales-profit').textContent = formatMoney(system.totalPhoneSalesProfit());
    document.getElementById('phone-stock-count').textContent = String(phoneStockCount);
    document.getElementById('phone-inventory-value').textContent = formatMoney(phoneInventoryValue);
    document.getElementById('open-tickets').textContent = String(system.getOpenRepairTickets().length);
    document.getElementById('low-stock-count').textContent = String(system.generateLowStockReport().length);
}

async function refreshUI() {
    const badge = document.getElementById('current-user-badge');
    if (badge) badge.textContent = await buildUserBadgeText(await getCurrentUserProfile());

    applyUserPreferences();

    const adminOnlyElements = document.querySelectorAll('[data-admin-only]');
    const isAdmin = await isAdminUser();
    adminOnlyElements.forEach(element => {
        element.classList.toggle('hidden', !isAdmin);
    });

    if (!isLoggedIn()) {
        showAuthScreen('login');
        return;
    }

    populateCategoryFilter();
    populateItemIdOptions();
    const filtered = getVisibleItems();
    renderInventory(filtered);
    renderPhoneInventory();
    renderTransactions();
    renderRepairTickets();
    populateTicketAutofill();
    renderStats();
    await populateAccountSettings();
    await renderStaffList();
}

// Used by handlers that actually change inventory/transactions/tickets:
// persists first, then re-renders. (refreshUI() alone is render-only — it's
// also invoked by the live-sync listener when a remote change arrives, and
// saving there would just echo the write straight back into another save.)
async function persistAndRefresh() {
    await saveOrgState();
    // New transactions introduce new createdBy values, so rebuild the staff filter.
    await populateReportUserFilter();
    refreshUI();
}

// Auto-navigate to a dashboard section after an action (the sidebar links remain
// fully manual too). Falls back to a no-op if the switcher isn't available.
function gotoSection(id) {
    if (typeof window.showSection === 'function') {
        window.showSection(id);
    }
}

// Fills the Account Settings panel with the signed-in user's details. Fields
// the user is actively editing are left alone so live refreshes don't clobber
// unsaved typing.
async function populateAccountSettings() {
    const profile = await getCurrentUserProfile();
    if (!profile) return;

    const usernameEl = document.getElementById('account-username');
    if (usernameEl) usernameEl.value = profile.username || '';
    const roleEl = document.getElementById('account-role');
    if (roleEl) roleEl.value = profile.role || 'staff';

    const nameEl = document.getElementById('account-full-name');
    if (nameEl && document.activeElement !== nameEl) nameEl.value = profile.fullName || '';
    const contactEl = document.getElementById('account-contact');
    if (contactEl && document.activeElement !== contactEl) contactEl.value = profile.contact || '';

    const summary = document.getElementById('account-summary');
    if (summary) {
        const org = await getOrganisationName(profile.orgId);
        summary.textContent = `${profile.fullName || profile.username} · ${org}`;
    }
}

// Admin-only roster of the current organisation's accounts.
async function renderStaffList() {
    const tbody = document.getElementById('staff-table');
    if (!tbody) return;
    const orgId = getCurrentOrgId();
    const users = (await getUsers()).filter(user => user.orgId === orgId);
    tbody.innerHTML = users.length
        ? users.map(user => `<tr>
            <td>${escapeHtml(user.fullName || '—')}</td>
            <td>${escapeHtml(user.username || '')}</td>
            <td>${escapeHtml(user.contact || '—')}</td>
            <td>${escapeHtml(user.role || 'staff')}</td>
          </tr>`).join('')
        : '<tr><td colspan="4">No staff accounts yet.</td></tr>';
}

async function handleSaveAccountSettings(event) {
    event.preventDefault();
    const profile = await getCurrentUserProfile();
    if (!profile) {
        alert('You need to be logged in to change your account settings.');
        return;
    }

    const fullName = document.getElementById('account-full-name').value.trim();
    const contact = document.getElementById('account-contact').value.trim();
    const newPassword = document.getElementById('account-new-password').value;

    if (!fullName) {
        alert('Please enter your full name.');
        return;
    }
    if (profile.role === 'staff' && !contact) {
        alert('A contact (email or phone) is required for staff accounts.');
        return;
    }
    if (newPassword && newPassword.length < 6) {
        alert('New password must be at least 6 characters.');
        return;
    }

    try {
        if (cloudAuthActive()) {
            const saved = await upsertUserProfile(profile.uid, {
                email: profile.username,
                fullName,
                contact,
                role: profile.role,
                orgId: profile.orgId
            });
            if (!saved) {
                alert('Could not save your changes — please try again.');
                return;
            }
            if (newPassword) {
                const current = firebaseAuth.currentUser;
                if (!current) {
                    alert('Your session expired — please log in again to change the password.');
                    return;
                }
                await current.updatePassword(newPassword);
            }
        } else {
            const updated = { ...profile, fullName, contact };
            if (newPassword) {
                const salt = generateSalt();
                updated.passwordSalt = salt;
                updated.passwordHash = await createPasswordHash(newPassword, salt);
            }
            const saved = await upsertUser(updated);
            if (!saved) {
                alert('Could not save your changes — please try again.');
                return;
            }
        }
        document.getElementById('account-new-password').value = '';
        alert(newPassword ? 'Account settings saved and password updated.' : 'Account settings saved.');
        refreshUI();
    } catch (error) {
        alert(describeAuthError(error));
    }
}

// Cloud mode: an admin creates a staff login without being signed out.
// Firebase's client SDK would otherwise swap the current session for the newly
// created user, so we create the account on a throwaway SECONDARY app and
// discard it afterwards, leaving the admin's own session untouched.
async function createStaffAccountInCloud(fullName, identifier, password, role, contact) {
    const secondary = firebase.initializeApp(firebaseConfig, `staff-creator-${Date.now()}`);
    try {
        const cred = await secondary.auth().createUserWithEmailAndPassword(identifier, password);
        const saved = await upsertUserProfile(cred.user.uid, {
            email: identifier,
            fullName,
            contact,
            role,
            orgId: getCurrentOrgId()
        });
        return { uid: cred.user.uid, saved };
    } finally {
        try { await secondary.auth().signOut(); } catch (err) { /* ignore */ }
        try { await secondary.delete(); } catch (err) { /* ignore */ }
    }
}

async function handleAddStaffAccount(event) {
    event.preventDefault();
    if (!(await isAdminUser())) {
        alert('Only admin users can add staff accounts.');
        return;
    }

    const fullName = document.getElementById('staff-name').value.trim();
    const identifier = document.getElementById('staff-username').value.trim();
    const password = document.getElementById('staff-password').value;
    const role = document.getElementById('staff-role').value;
    const contact = document.getElementById('staff-contact').value.trim();

    if (!fullName || !identifier || !password) {
        alert('Please fill in the name, username, and password.');
        return;
    }
    if (!contact) {
        alert('A contact (email or phone) is required for staff accounts.');
        return;
    }
    if (password.length < 6) {
        alert('Password must be at least 6 characters.');
        return;
    }

    const users = await getUsers();
    if (users.some(user => user.username.toLowerCase() === identifier.toLowerCase())) {
        alert('That username already exists');
        return;
    }

    if (cloudAuthActive()) {
        try {
            const { saved } = await createStaffAccountInCloud(fullName, identifier, password, role, contact);
            if (!saved) {
                alert('The login was created, but saving the profile failed — please try again.');
                return;
            }
            document.getElementById('add-staff-form').reset();
            alert(`Staff account created for ${fullName} (contact: ${contact}).`);
            await renderStaffList();
        } catch (err) {
            alert(describeAuthError(err));
        }
        return;
    }

    const salt = generateSalt();
    const saved = await upsertUser({
        username: identifier,
        fullName,
        contact,
        role,
        orgId: getCurrentOrgId(),
        passwordSalt: salt,
        passwordHash: await createPasswordHash(password, salt)
    });
    if (!saved) {
        alert('Could not create the account — please try again.');
        return;
    }

    document.getElementById('add-staff-form').reset();
    alert(`Staff account created for ${fullName} (contact: ${contact}).`);
    await renderStaffList();
}

async function handleAddItem(event) {
    event.preventDefault();
    const itemData = {
        id: document.getElementById('item-id').value.trim(),
        name: document.getElementById('item-name').value.trim(),
        category: document.getElementById('item-category').value.trim() || 'General',
        unit: document.getElementById('item-unit').value.trim() || 'piece',
        stock: Number(document.getElementById('item-stock').value),
        minStock: Number(document.getElementById('item-min-stock').value),
        costPrice: Number(document.getElementById('item-cost').value),
        sellingPrice: Number(document.getElementById('item-selling').value)
    };

    try {
        if (apiMode) {
            await orgApi('/items', 'POST', itemData);
            event.target.reset();
            await populateReportUserFilter();
            refreshUI();
            alert('Item added successfully');
            gotoSection('sec-inventory');
            return;
        }
        system.addItem(itemData);
        event.target.reset();
        await persistAndRefresh();
        alert('Item added successfully');
        gotoSection('sec-inventory');
    } catch (error) {
        alert(error.message);
    }
}

async function handleStockAction(type) {
    const id = document.getElementById('stock-id').value.trim();
    const amount = Number(document.getElementById('stock-amount').value);
    const reason = document.getElementById('stock-reason').value.trim();

    if (apiMode) {
        const resolvedReason = reason || (type === 'in' ? 'Supplier delivery' : 'Repair used');
        try {
            await orgApi(`/items/${encodeURIComponent(id)}/${type === 'in' ? 'stock-in' : 'stock-out'}`, 'POST', { amount, reason: resolvedReason });
            document.getElementById('stock-id').value = '';
            document.getElementById('stock-amount').value = '';
            document.getElementById('stock-reason').value = '';
            await populateReportUserFilter();
            refreshUI();
            gotoSection('sec-inventory');
        } catch (error) {
            alert(error.message);
        }
        return;
    }

    try {
        if (type === 'in') {
            system.stockIn(id, amount, reason || 'Supplier delivery');
            alert('Stock added successfully');
        } else {
            system.stockOut(id, amount, reason || 'Repair used');
            alert('Stock removed successfully');
        }
        document.getElementById('stock-id').value = '';
        document.getElementById('stock-amount').value = '';
        document.getElementById('stock-reason').value = '';
        await persistAndRefresh();
        gotoSection('sec-inventory');
    } catch (error) {
        alert(error.message);
    }
}

async function handleSellItem() {
    const id = document.getElementById('sale-id').value.trim();
    const qty = Number(document.getElementById('sale-qty').value);
    const customer = document.getElementById('sale-customer').value.trim() || 'Walk-in';

    try {
        const result = apiMode
            ? await orgApi('/sales', 'POST', { id, amount: qty, customer, reason: 'Sold to customer' })
            : system.sellItem(id, qty, customer, 'Sold to customer');
        document.getElementById('sale-id').value = '';
        document.getElementById('sale-qty').value = '';
        alert(`Sale recorded. Revenue: ${formatMoney(result.revenue)}, Profit: ${formatMoney(result.profit)}`);
        if (apiMode) {
            await populateReportUserFilter();
            refreshUI();
        } else {
            await persistAndRefresh();
        }
        gotoSection('sec-logs');
    } catch (error) {
        alert(error.message);
    }
}

async function handlePhoneTrade(type) {
    const brand = document.getElementById('phone-brand').value.trim();
    const model = document.getElementById('phone-model').value.trim();
    const condition = document.getElementById('phone-condition').value.trim() || 'Grade A';
    const price = Number(document.getElementById(type === 'buy' ? 'phone-buy-price' : 'phone-sell-price').value);
    const qty = Number(document.getElementById('phone-qty').value);

    if (!brand || !model || !qty || qty <= 0 || !price || price <= 0) {
        alert('Please complete the brand, model, quantity, and price fields.');
        return;
    }

    if (apiMode) {
        try {
            const result = await orgApi('/phone-trades', 'POST', { type, brand, model, condition, price, qty });
            if (type === 'buy') {
                alert(`Phone stock updated: ${qty} ${brand} ${model} added to inventory.`);
            } else {
                if (result && result.receipt) {
                    lastPhoneSale = { ...result.receipt, date: new Date().toLocaleString() };
                    document.getElementById('print-phone-sale-btn').classList.remove('hidden');
                }
                alert(`Phone stock updated: ${qty} ${brand} ${model} sold.`);
            }
            document.getElementById('phone-brand').value = '';
            document.getElementById('phone-model').value = '';
            document.getElementById('phone-condition').value = 'Grade A';
            document.getElementById('phone-buy-price').value = '';
            document.getElementById('phone-sell-price').value = '';
            document.getElementById('phone-qty').value = '';
            await populateReportUserFilter();
            refreshUI();
            gotoSection(type === 'buy' ? 'sec-inventory' : 'sec-logs');
        } catch (error) {
            alert(error.message);
        }
        return;
    }

    const phoneId = `phone-${brand.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-${model.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
    let item = system.getItem(phoneId);

    if (!item && type === 'buy') {
        system.addItem({
            id: phoneId,
            name: `${brand} ${model}`,
            category: 'Phone',
            unit: 'piece',
            stock: 0,
            minStock: 1,
            costPrice: price,
            sellingPrice: price * 1.5,
            condition
        });
        item = system.getItem(phoneId);
    }

    if (!item) {
        alert('This phone is not in stock yet. Buy it first.');
        return;
    }

    if (type === 'buy') {
        const prior = item.stock;
        system.stockIn(phoneId, qty, `Phone purchase - ${condition}`);
        const lastEntry = system.transactions[system.transactions.length - 1];
        if (lastEntry) {
            lastEntry.type = 'phone-buy';
            lastEntry.customer = 'Supplier';
            lastEntry.reason = `Phone purchase - ${condition}`;
            lastEntry.costPrice = price;
            lastEntry.stockBefore = prior;
        }

        item.costPrice = price;
        item.sellingPrice = Math.max(price * 1.5, price + 50);
        item.condition = condition;
        item.touch();
        alert(`Phone stock updated: ${qty} ${brand} ${model} added to inventory.`);
    } else {
        if (qty > item.stock) {
            alert(`Not enough phone stock. Available: ${item.stock}`);
            return;
        }

        const prior = item.stock;
        system.stockOut(phoneId, qty, `Phone sale - ${condition}`);
        const lastEntry = system.transactions[system.transactions.length - 1];
        if (lastEntry) {
            lastEntry.type = 'phone-sale';
            lastEntry.customer = 'Walk-in';
            lastEntry.reason = `Phone sale - ${condition}`;
            lastEntry.revenue = qty * price;
            lastEntry.profit = qty * (price - (item.costPrice || 0));
            lastEntry.stockBefore = prior;
        }

        item.condition = condition;
        item.touch();
        lastPhoneSale = {
            title: 'Phone Sale Receipt',
            item: `${brand} ${model}`,
            condition,
            quantity: qty,
            price,
            total: qty * price,
            date: new Date().toLocaleString(),
            transactionId: `PHONE-${Date.now()}`
        };
        document.getElementById('print-phone-sale-btn').classList.remove('hidden');
        alert(`Phone stock updated: ${qty} ${brand} ${model} sold.`);
    }

    document.getElementById('phone-brand').value = '';
    document.getElementById('phone-model').value = '';
    document.getElementById('phone-condition').value = 'Grade A';
    document.getElementById('phone-buy-price').value = '';
    document.getElementById('phone-sell-price').value = '';
    document.getElementById('phone-qty').value = '';
    await persistAndRefresh();
    gotoSection(type === 'buy' ? 'sec-inventory' : 'sec-logs');
}

async function handleDeleteItem() {
    if (!(await isAdminUser())) {
        alert('Only admin users can delete inventory items.');
        return;
    }

    const id = document.getElementById('delete-item-id').value.trim();

    try {
        const item = apiMode
            ? await orgApi(`/items/${encodeURIComponent(id)}`, 'DELETE')
            : system.deleteItem(id);
        document.getElementById('delete-item-id').value = '';
        alert(`Deleted item: ${item.name}`);
        if (apiMode) {
            await populateReportUserFilter();
            refreshUI();
        } else {
            await persistAndRefresh();
        }
        gotoSection('sec-inventory');
    } catch (error) {
        alert(error.message);
    }
}

async function handleRepairTicket() {
    const payload = {
        customerName: document.getElementById('repair-customer').value.trim(),
        deviceModel: document.getElementById('repair-device').value.trim(),
        itemId: document.getElementById('repair-id').value.trim(),
        quantityUsed: Number(document.getElementById('repair-qty').value),
        problem: document.getElementById('repair-problem').value.trim()
    };

    try {
        const ticket = apiMode
            ? await orgApi('/repairs', 'POST', payload)
            : system.createRepairTicket(payload);
        document.getElementById('repair-customer').value = '';
        document.getElementById('repair-device').value = '';
        document.getElementById('repair-id').value = '';
        document.getElementById('repair-qty').value = '';
        document.getElementById('repair-problem').value = '';
        alert(`Repair ticket created: ${ticket.ticketId}`);
        if (apiMode) {
            await populateReportUserFilter();
            refreshUI();
        } else {
            await persistAndRefresh();
        }
        gotoSection('sec-logs');
    } catch (error) {
        alert(error.message);
    }
}

async function handleCompleteRepairTicket() {
    const ticketId = document.getElementById('repair-complete-id').value.trim();

    try {
        const ticket = apiMode
            ? await orgApi(`/repairs/${encodeURIComponent(ticketId)}/complete`, 'POST')
            : system.completeRepairTicket(ticketId);
        document.getElementById('repair-complete-id').value = '';
        lastCompletedTicket = {
            title: 'Completed Repair Ticket',
            ticketId: ticket.ticketId,
            customer: ticket.customerName,
            device: ticket.deviceModel,
            itemId: ticket.itemId,
            quantity: ticket.quantityUsed,
            problem: ticket.problem,
            date: new Date().toLocaleString()
        };
        document.getElementById('print-repair-btn').classList.remove('hidden');
        alert(`Ticket ${ticket.ticketId} marked as completed.`);
        if (apiMode) {
            await populateReportUserFilter();
            refreshUI();
        } else {
            await persistAndRefresh();
        }
        gotoSection('sec-logs');
    } catch (error) {
        alert(error.message);
    }
}

// Lets the user autofill the "Complete ticket ID" field by choosing a recent
// ticket from a dropdown instead of pasting/typing the ID. Open tickets are
// listed first so the ones that need completing are easiest to reach.
function populateTicketAutofill() {
    const select = document.getElementById('autofill-ticket-id');
    if (!select) return;

    const rank = ticket => (ticket.status === 'Open' ? 0 : 1);
    const tickets = [...(system.repairTickets || [])].sort((a, b) =>
        rank(a) - rank(b) || String(b.ticketId).localeCompare(String(a.ticketId)));

    const options = ['<option value="">Autofill ticket ID…</option>'];
    for (const ticket of tickets) {
        const label = `${ticket.ticketId} — ${ticket.customerName || 'customer'} (${ticket.deviceModel || 'device'}) · ${ticket.status || 'Open'}`;
        options.push(`<option value="${escapeHtml(ticket.ticketId)}">${escapeHtml(label)}</option>`);
    }
    select.innerHTML = options.join('');
    select.value = '';
}

// ---- Printing: Web Bluetooth (ESC/POS) + network printer ------------------
// Lets the app print receipts, the inventory list and the sales report either
// to a Bluetooth thermal printer (Web Bluetooth) or to a network / WiFi
// printer through the Java backend, falling back to the browser print dialog.
const PRINTER_STORAGE_KEY = 'mobile-repair-printer';
const ESC = 0x1b;
const GS = 0x1d;
// Common BLE service UUIDs advertised by ESC/POS thermal printers.
const BLE_PRINTER_SERVICES = [
    '000018f0-0000-1000-8000-00805f9b34fb',
    '0000ff00-0000-1000-8000-00805f9b34fb',
    '0000ffe0-0000-1000-8000-00805f9b34fb',
    '0000fff0-0000-1000-8000-00805f9b34fb',
    '0000ff10-0000-1000-8000-00805f9b34fb',
    '49535343-fe7d-4ae5-8fa9-9fafd205e455',
    'e7810a71-73ae-499d-8c15-faa9aef0c3f2'
];

let printerSettings = { type: 'system', host: '', port: 9100 };
let bluetoothPrinter = null; // { device, characteristic }

function loadPrinterSettings() {
    try {
        const raw = localStorage.getItem(PRINTER_STORAGE_KEY);
        if (raw) printerSettings = { ...printerSettings, ...JSON.parse(raw) };
    } catch { /* ignore corrupt settings */ }
}

function savePrinterSettings() {
    try { localStorage.setItem(PRINTER_STORAGE_KEY, JSON.stringify(printerSettings)); } catch { /* ignore */ }
}

function printerLabel() {
    if (printerSettings.type === 'bluetooth') {
        return bluetoothPrinter && bluetoothPrinter.device
            ? `Bluetooth: ${bluetoothPrinter.device.name || 'printer'} connected`
            : 'Bluetooth: not connected';
    }
    if (printerSettings.type === 'network') {
        return printerSettings.host
            ? `Network: ${printerSettings.host}:${printerSettings.port || 9100}`
            : 'Network: no printer address set';
    }
    return 'System print dialog';
}

function renderPrinterStatus() {
    const status = document.getElementById('printer-status');
    if (status) status.textContent = printerLabel();

    const name = document.getElementById('printer-bluetooth-name');
    if (name) name.textContent = bluetoothPrinter && bluetoothPrinter.device
        ? `Connected to ${bluetoothPrinter.device.name || 'printer'}` : '';

    const typeSelect = document.getElementById('printer-type');
    if (typeSelect) typeSelect.value = printerSettings.type;

    const hostInput = document.getElementById('printer-host');
    if (hostInput && document.activeElement !== hostInput && hostInput.value !== (printerSettings.host || '')) {
        hostInput.value = printerSettings.host || '';
    }
    const portInput = document.getElementById('printer-port');
    if (portInput && document.activeElement !== portInput && portInput.value !== String(printerSettings.port || 9100)) {
        portInput.value = printerSettings.port || 9100;
    }

    const btPanel = document.getElementById('printer-bluetooth-panel');
    if (btPanel) btPanel.classList.toggle('hidden', printerSettings.type !== 'bluetooth');
    const netPanel = document.getElementById('printer-network-panel');
    if (netPanel) netPanel.classList.toggle('hidden', printerSettings.type !== 'network');
}

// ---- ESC/POS byte building ----

function escposChars(text) {
    return Array.from(String(text ?? '')).map(char => char.charCodeAt(0) & 0xff);
}

// Builds an ESC/POS command stream for a simple heading + label/value list,
// which is what receipts, the inventory list and the report all reduce to.
function buildEscPos(doc) {
    const bytes = [];
    const push = (...values) => values.forEach(value => bytes.push(value));
    const text = value => escposChars(value).forEach(byte => bytes.push(byte));

    push(ESC, 0x40);       // initialise printer
    push(ESC, 0x74, 0x00); // code page 0 (CP437)

    push(ESC, 0x61, 0x01); // centre align
    push(ESC, 0x45, 0x01); // bold on
    push(GS, 0x21, 0x11);  // double width + height
    text('Mobile Repair Shop\n');
    push(GS, 0x21, 0x00);  // back to normal size
    push(ESC, 0x45, 0x00); // bold off
    if (doc.subtitle) text(doc.subtitle + '\n');
    text('\n');

    push(ESC, 0x61, 0x00); // left align
    for (const [label, value] of doc.rows || []) {
        text(`${label}: ${value}\n`);
    }
    if (doc.footer) {
        text('\n');
        push(ESC, 0x61, 0x01); // centre
        text(doc.footer + '\n');
    }
    push(ESC, 0x64, 0x03); // feed 3 lines
    push(GS, 0x56, 0x00);  // full cut
    return new Uint8Array(bytes);
}

function prettyLabel(key) {
    return String(key).replace(/([A-Z])/g, ' $1').replace(/^./, char => char.toUpperCase()).trim();
}

function receiptToDoc(receipt) {
    const rows = Object.entries(receipt)
        .filter(([key]) => key !== 'title')
        .map(([key, value]) => [prettyLabel(key), value]);
    return { subtitle: receipt.title, rows, footer: 'Thank you!' };
}

function inventoryToDoc(items) {
    const rows = items.map(item => [item.name, `x${item.stock}  ${formatMoney(item.sellingPrice)}`]);
    return { subtitle: 'Inventory', rows, footer: `${items.length} item(s)` };
}

function reportToDoc(transactions) {
    const rows = transactions.slice(-20).map(txn => [
        new Date(txn.date).toLocaleDateString(),
        `${txn.type} ${txn.name}${txn.revenue ? ' ' + formatMoney(txn.revenue) : ''}`
    ]);
    const revenue = sumByType(transactions, ['sale', 'phone-sale'], 'revenue');
    const profit = sumByType(transactions, ['sale', 'phone-sale'], 'profit');
    rows.push(['Revenue', formatMoney(revenue)]);
    rows.push(['Profit', formatMoney(profit)]);
    return { subtitle: 'Sales Report', rows, footer: `${transactions.length} transaction(s)` };
}

function bytesToBase64(bytes) {
    let binary = '';
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
        binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    }
    return btoa(binary);
}

// ---- Transports ----

async function sendToNetworkPrinter(bytes) {
    const response = await fetch('/api/print/network', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            host: printerSettings.host,
            port: Number(printerSettings.port) || 9100,
            data: bytesToBase64(bytes)
        })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Network printing failed.');
    return data;
}

async function testNetworkPrinter() {
    const response = await fetch('/api/print/network/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ host: printerSettings.host, port: Number(printerSettings.port) || 9100 })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Could not reach the printer.');
    return data;
}

async function connectBluetoothPrinter() {
    if (!navigator.bluetooth) {
        throw new Error('Web Bluetooth is not available in this browser. Use Chrome or Edge over HTTPS or localhost.');
    }
    const device = await navigator.bluetooth.requestDevice({
        acceptAllDevices: true,
        optionalServices: BLE_PRINTER_SERVICES
    });
    const server = await device.gatt.connect();
    const services = await server.getPrimaryServices();
    let characteristic = null;
    for (const service of services) {
        const characteristics = await service.getCharacteristics();
        characteristic = characteristics.find(c =>
            c.properties.write || c.properties.writeWithoutResponse || c.properties.writeWithoutFeedback);
        if (characteristic) break;
    }
    if (!characteristic) {
        try { device.gatt.disconnect(); } catch { /* ignore */ }
        throw new Error('No writable characteristic found — is this an ESC/POS printer?');
    }
    bluetoothPrinter = { device, characteristic };
    device.addEventListener('gattserverdisconnected', () => {
        bluetoothPrinter = null;
        renderPrinterStatus();
    });
    return device;
}

async function writeToBluetoothPrinter(bytes) {
    if (!bluetoothPrinter || !bluetoothPrinter.characteristic) {
        throw new Error('No Bluetooth printer connected.');
    }
    const characteristic = bluetoothPrinter.characteristic;
    const chunkSize = 180;
    for (let i = 0; i < bytes.length; i += chunkSize) {
        const packet = bytes.subarray(i, i + chunkSize);
        if (characteristic.properties.writeWithoutResponse) {
            await characteristic.writeValueWithoutResponse(packet);
        } else {
            await characteristic.writeValue(packet);
        }
    }
}

// Sends bytes to whichever direct printer is configured. Throws if none works;
// callers fall back to the browser print dialog.
async function printBytes(bytes) {
    if (printerSettings.type === 'bluetooth') {
        await writeToBluetoothPrinter(bytes);
        return 'bluetooth';
    }
    if (printerSettings.type === 'network') {
        if (!printerSettings.host) throw new Error('Set the network printer address first.');
        await sendToNetworkPrinter(bytes);
        return 'network';
    }
    throw new Error('No direct printer is configured.');
}

function printHtmlWindow(title, headers, rows) {
    const printWindow = window.open('', '_blank', 'width=900,height=650');
    if (!printWindow) {
        alert('Please allow pop-ups to print.');
        return;
    }
    const head = headers.map(header => `<th>${escapeHtml(header)}</th>`).join('');
    const body = rows.map(cells => `<tr>${cells.map(cell => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`).join('');
    printWindow.document.write(`<!doctype html><html><head><title>${escapeHtml(title)}</title><style>
        body { font-family: Arial, sans-serif; padding: 32px; color: #111827; }
        h1 { margin-bottom: 24px; } table { border-collapse: collapse; width: 100%; }
        th, td { border-bottom: 1px solid #d1d5db; padding: 8px 10px; text-align: left; }
        th { text-transform: capitalize; }
    </style></head><body><h1>${escapeHtml(title)}</h1><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></body></html>`);
    printWindow.document.close();
    printWindow.focus();
    printWindow.print();
}

// ---- Print actions ----

async function printReceipt(receipt) {
    if (!receipt) return;

    if (printerSettings.type !== 'system') {
        try {
            await printBytes(buildEscPos(receiptToDoc(receipt)));
            return;
        } catch (error) {
            alert((error.message || 'Printing failed.') + ' Falling back to the system print dialog.');
        }
    }

    const rows = Object.entries(receipt)
        .filter(([key]) => key !== 'title')
        .map(([key, value]) => `<tr><th>${escapeHtml(key)}</th><td>${escapeHtml(value)}</td></tr>`)
        .join('');
    const printWindow = window.open('', '_blank', 'width=760,height=650');
    if (!printWindow) {
        alert('Please allow pop-ups to print the receipt.');
        return;
    }

    const title = escapeHtml(receipt.title);
    printWindow.document.write(`<!doctype html><html><head><title>${title}</title><style>
        body { font-family: Arial, sans-serif; padding: 32px; color: #111827; }
        h1 { margin-bottom: 24px; } table { border-collapse: collapse; width: 100%; max-width: 620px; }
        th, td { border-bottom: 1px solid #d1d5db; padding: 10px; text-align: left; }
        th { width: 35%; text-transform: capitalize; }
    </style></head><body><h1>${title}</h1><table>${rows}</table></body></html>`);
    printWindow.document.close();
    printWindow.focus();
    printWindow.print();
}

async function printInventory() {
    const items = getVisibleItems();
    if (!items.length) {
        alert('No inventory items to print.');
        return;
    }
    if (printerSettings.type !== 'system') {
        try { await printBytes(buildEscPos(inventoryToDoc(items))); return; }
        catch (error) { alert((error.message || 'Printing failed.') + ' Falling back to the system print dialog.'); }
    }
    const rows = items.map(item => [
        item.id, item.name, item.category, item.stock,
        formatMoney(item.costPrice), formatMoney(item.sellingPrice), item.isLowStock ? 'Low' : 'OK'
    ]);
    printHtmlWindow('Inventory', ['ID', 'Name', 'Category', 'Stock', 'Cost', 'Selling', 'Status'], rows);
}

async function printSalesReport() {
    const transactions = filterTransactions();
    if (printerSettings.type !== 'system') {
        try { await printBytes(buildEscPos(reportToDoc(transactions))); return; }
        catch (error) { alert((error.message || 'Printing failed.') + ' Falling back to the system print dialog.'); }
    }
    const rows = transactions.slice(-20).map(txn => [
        txn.type, txn.name, txn.amount || 0, txn.customer || '',
        new Date(txn.date).toLocaleString()
    ]);
    printHtmlWindow('Sales Report', ['Type', 'Item', 'Amount', 'Customer', 'Date'], rows);
}

async function handleTestPrint() {
    try {
        const doc = {
            subtitle: 'Test print',
            rows: [['Status', 'OK'], ['Time', new Date().toLocaleString()]],
            footer: 'Mobile Repair Stock System'
        };
        if (printerSettings.type === 'bluetooth') {
            await writeToBluetoothPrinter(buildEscPos(doc));
            alert('Test page sent to the Bluetooth printer.');
            return;
        }
        if (printerSettings.type === 'network') {
            if (!printerSettings.host) throw new Error('Set the network printer address first.');
            await sendToNetworkPrinter(buildEscPos(doc));
            alert('Test page sent to the network printer.');
            return;
        }
        printHtmlWindow('Test print', ['Field', 'Value'], [['Status', 'OK'], ['Time', new Date().toLocaleString()]]);
    } catch (error) {
        alert(error.message || 'Test print failed.');
    }
}

async function handlePrinterTypeChange(event) {
    printerSettings.type = event.target.value;
    if (printerSettings.type === 'network') {
        printerSettings.host = document.getElementById('printer-host').value.trim();
        printerSettings.port = Number(document.getElementById('printer-port').value) || 9100;
    }
    savePrinterSettings();
    renderPrinterStatus();
}

function handlePrinterSave() {
    printerSettings.host = document.getElementById('printer-host').value.trim();
    printerSettings.port = Number(document.getElementById('printer-port').value) || 9100;
    savePrinterSettings();
    renderPrinterStatus();
    alert('Printer settings saved.');
}

async function handleBluetoothConnect() {
    try {
        const device = await connectBluetoothPrinter();
        printerSettings.type = 'bluetooth';
        savePrinterSettings();
        renderPrinterStatus();
        alert(`Connected to ${device.name || 'Bluetooth printer'}.`);
    } catch (error) {
        alert(error.message || 'Could not connect to the Bluetooth printer.');
    }
}

function handleBluetoothDisconnect() {
    if (bluetoothPrinter && bluetoothPrinter.device) {
        try { bluetoothPrinter.device.gatt.disconnect(); } catch { /* ignore */ }
    }
    bluetoothPrinter = null;
    renderPrinterStatus();
    alert('Bluetooth printer disconnected.');
}

async function savePreferences({ currency, language }) {
    const orgId = getCurrentOrgId();
    if (!orgId) return false;
    currentPreferences = { ...DEFAULT_PREFERENCES, ...currentPreferences, currency, language };
    if (apiMode) {
        await orgApi('/preferences', 'PUT', { currency, language });
        return true;
    }
    await saveOrgState(orgId);
    return true;
}

async function handleSavePreferences(event) {
    event.preventDefault();
    const currency = document.getElementById('settings-currency').value;
    const language = document.getElementById('settings-language').value;

    if (!(await savePreferences({ currency, language }))) {
        alert('Please log in to save your preferences.');
        return;
    }
    alert('Preferences saved successfully.');
    refreshUI();
}

async function handleQuickCurrencyChange(event) {
    const currency = event.target.value;
    const { language } = currentPreferences;
    if (await savePreferences({ currency, language })) refreshUI();
}

// Query string describing the current inventory view, sent to the backend's
// report endpoints so the exported file matches what's on screen.
function inventoryExportQuery() {
    const q = document.getElementById('search-box')?.value || '';
    const category = document.getElementById('category-filter')?.value || 'all';
    return `?q=${encodeURIComponent(q)}&category=${encodeURIComponent(category)}`;
}

async function exportInventoryToExcel() {
    if (apiMode) {
        try {
            if (!getVisibleItems().length) {
                alert('No inventory items to export.');
                return;
            }
            const timestamp = new Date().toISOString().slice(0, 10);
            await orgDownload(`/reports/inventory.xlsx${inventoryQuery()}`, `inventory-${timestamp}.xlsx`);
        } catch (error) {
            alert(error.message || 'Excel export failed — please try again.');
        }
        return;
    }

    if (typeof XLSX === 'undefined') {
        alert('Excel export library failed to load. Check your connection and try again.');
        return;
    }

    const items = getVisibleItems();
    if (!items.length) {
        alert('No inventory items to export.');
        return;
    }

    const rows = items.map(item => ({
        'Item ID': item.id,
        'Name': item.name,
        'Category': item.category,
        'Unit': item.unit,
        'Stock': item.stock,
        'Min Stock': item.minStock,
        'Cost Price': item.costPrice,
        'Selling Price': item.sellingPrice,
        'Status': item.isLowStock ? 'Low' : 'OK'
    }));

    const worksheet = XLSX.utils.json_to_sheet(rows);
    worksheet['!cols'] = [
        { wch: 22 }, { wch: 26 }, { wch: 16 }, { wch: 10 },
        { wch: 8 }, { wch: 10 }, { wch: 12 }, { wch: 12 }, { wch: 8 }
    ];

    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Inventory');

    const timestamp = new Date().toISOString().slice(0, 10);
    XLSX.writeFile(workbook, `inventory-${timestamp}.xlsx`);
}

// Plain CSV export that needs no external library and opens in any
// spreadsheet app (or a text editor / Google Sheets), unlike .xlsx which
// requires Excel/LibreOffice to be installed.
async function exportInventoryToCsv() {
    if (apiMode) {
        try {
            const items = getVisibleItems();
            if (!items.length) {
                alert('No inventory items to export.');
                return;
            }
            const timestamp = new Date().toISOString().slice(0, 10);
            await orgDownload(`/reports/inventory.csv${inventoryQuery()}`, `inventory-${timestamp}.csv`);
        } catch (error) {
            alert(error.message || 'CSV export failed — please try again.');
        }
        return;
    }

    const items = getVisibleItems();
    if (!items.length) {
        alert('No inventory items to export.');
        return;
    }

    const headers = ['Item ID', 'Name', 'Category', 'Unit', 'Stock', 'Min Stock', 'Cost Price', 'Selling Price', 'Status'];
    const cell = value => {
        const text = String(value ?? '');
        return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
    };
    const rows = items.map(item => [
        item.id, item.name, item.category, item.unit, item.stock,
        item.minStock, item.costPrice, item.sellingPrice, item.isLowStock ? 'Low' : 'OK'
    ].map(cell).join(','));

    const csv = [headers.join(','), ...rows].join('\r\n');
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    const timestamp = new Date().toISOString().slice(0, 10);
    link.href = url;
    link.download = `inventory-${timestamp}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

async function downloadReport() {
    if (apiMode) {
        try {
            const params = new URLSearchParams({
                from: document.getElementById('report-start-date')?.value || '',
                to: document.getElementById('report-end-date')?.value || '',
                user: document.getElementById('report-user-filter')?.value || 'all',
                type: document.getElementById('report-type-filter')?.value || 'all'
            });
            await orgDownload(`/reports/report.json?${params.toString()}`, 'mobile-repair-report.json');
        } catch (error) {
            alert(error.message || 'Could not download the report — please try again.');
        }
        return;
    }

    const filters = {
        startDate: document.getElementById('report-start-date')?.value,
        endDate: document.getElementById('report-end-date')?.value,
        selectedUser: document.getElementById('report-user-filter')?.value || 'all',
        reportType: document.getElementById('report-type-filter')?.value || 'all'
    };
    const filteredTransactions = filterTransactions();
    const inventory = system.listItems();

    const report = {
        generatedAt: new Date().toISOString(),
        user: getCurrentUserKey(),
        filters,
        inventory,
        transactions: filteredTransactions,
        repairTickets: system.repairTickets,
        summary: {
            totalItems: inventory.length,
            inventoryValue: system.inventoryValue(),
            salesRevenue: sumByType(filteredTransactions, ['sale', 'phone-sale'], 'revenue'),
            salesProfit: sumByType(filteredTransactions, ['sale', 'phone-sale'], 'profit'),
            regularSalesRevenue: sumByType(filteredTransactions, 'sale', 'revenue'),
            regularSalesProfit: sumByType(filteredTransactions, 'sale', 'profit'),
            phoneSalesRevenue: sumByType(filteredTransactions, 'phone-sale', 'revenue'),
            phoneSalesProfit: sumByType(filteredTransactions, 'phone-sale', 'profit'),
            lowStockItems: system.generateLowStockReport().length,
            openTickets: system.getOpenRepairTickets().length
        }
    };

    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.href = url;
    link.download = 'mobile-repair-report.json';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

async function resetDemoData() {
    if (!(await isAdminUser())) {
        alert('Only admin users can reset the demo data.');
        return;
    }

    const confirmed = window.confirm('Reset all data back to the original demo inventory for this organisation?');
    if (!confirmed) return;

    const orgId = getCurrentOrgId();
    if (!orgId) {
        alert('No organisation is currently loaded.');
        return;
    }

    if (apiMode) {
        try {
            await orgApi('/reset', 'POST');
            await populateReportUserFilter();
            refreshUI();
            alert('Demo data reset successfully for this organisation.');
        } catch (error) {
            alert(error.message || 'Could not reset the data — please try again.');
        }
        return;
    }

    // storeSetOrgData writes to Firestore when cloud sync is active and to
    // localStorage otherwise, so the reset actually takes effect in both modes.
    const reset = await storeSetOrgData(orgId, {
        items: DEFAULT_ITEMS.map(item => ({ ...item })),
        transactions: [],
        repairTickets: [],
        deletedItems: {},
        preferences: { ...DEFAULT_PREFERENCES },
        updatedAt: new Date().toISOString()
    });
    if (!reset) {
        alert('Could not reset the data — please try again.');
        return;
    }

    await loadOrgState(orgId);
    refreshUI();
    alert('Demo data reset successfully for this organisation.');
}

function setupThemeToggle() {
    const button = document.getElementById('theme-toggle-btn');
    const savedTheme = localStorage.getItem('repair-shop-theme');

    if (savedTheme === 'dark') {
        document.body.classList.add('dark-mode');
        button.textContent = 'Light Mode';
    }

    button.addEventListener('click', () => {
        const isDark = document.body.classList.toggle('dark-mode');
        localStorage.setItem('repair-shop-theme', isDark ? 'dark' : 'light');
        button.textContent = isDark ? 'Light Mode' : 'Dark Mode';
    });
}

async function init() {
    await detectApi();
    if (apiMode) await loadRemoteI18n();
    loadPrinterSettings();
    setupThemeToggle();

    const syncStatus = document.getElementById('sync-status');
    if (syncStatus) {
        if (cloudSyncActive()) {
            syncStatus.classList.add('synced');
            syncStatus.title = 'Cloud sync active — changes appear on every device';
        } else {
            syncStatus.classList.add('local-only');
            syncStatus.title = 'Local only — changes stay on this device/browser (Firebase not configured)';
        }
    }

    const demoHint = document.getElementById('demo-account-hint');
    if (demoHint) {
        demoHint.textContent = cloudAuthActive()
            ? 'No demo account here — sign up with your real email to create or join your organisation.'
            : 'Demo organisation: "Demo Repair Shop" — admin/admin123 · staff/staff123';
    }

    document.getElementById('login-form').addEventListener('submit', handleLoginSubmit);
    document.getElementById('signup-form').addEventListener('submit', handleSignupSubmit);
    document.getElementById('toggle-auth-mode').addEventListener('click', () => {
        const loginForm = document.getElementById('login-form');
        const signupForm = document.getElementById('signup-form');
        const isLoginVisible = !loginForm.classList.contains('hidden');
        if (isLoginVisible) {
            showAuthScreen('signup');
        } else {
            showAuthScreen('login');
        }
    });

    document.getElementById('logout-btn').addEventListener('click', handleLogout);
    document.getElementById('topbar-currency').addEventListener('change', handleQuickCurrencyChange);
    document.getElementById('preferences-form').addEventListener('submit', handleSavePreferences);
    document.getElementById('item-form').addEventListener('submit', handleAddItem);
    document.getElementById('stock-in-btn').addEventListener('click', () => handleStockAction('in'));
    document.getElementById('stock-out-btn').addEventListener('click', () => handleStockAction('out'));
    document.getElementById('delete-item-btn').addEventListener('click', handleDeleteItem);
    document.getElementById('sell-btn').addEventListener('click', handleSellItem);
    document.getElementById('phone-buy-btn').addEventListener('click', () => handlePhoneTrade('buy'));
    document.getElementById('phone-sell-btn').addEventListener('click', () => handlePhoneTrade('sell'));
    document.getElementById('repair-btn').addEventListener('click', handleRepairTicket);
    document.getElementById('complete-repair-btn').addEventListener('click', handleCompleteRepairTicket);
    document.getElementById('autofill-ticket-id').addEventListener('change', event => {
        if (!event.target.value) return;
        document.getElementById('repair-complete-id').value = event.target.value;
        event.target.value = '';
    });
    document.getElementById('print-repair-btn').addEventListener('click', () => printReceipt(lastCompletedTicket));
    document.getElementById('print-phone-sale-btn').addEventListener('click', () => printReceipt(lastPhoneSale));
    document.getElementById('search-box').addEventListener('input', refreshUI);
    document.getElementById('category-filter').addEventListener('change', refreshUI);
    document.getElementById('report-type-filter').addEventListener('change', refreshUI);
    document.getElementById('refresh-btn').addEventListener('click', refreshUI);
    document.getElementById('download-report-btn').addEventListener('click', downloadReport);
    document.getElementById('export-inventory-excel-btn').addEventListener('click', exportInventoryToExcel);
    document.getElementById('export-inventory-csv-btn').addEventListener('click', exportInventoryToCsv);
    document.getElementById('reset-data-btn').addEventListener('click', resetDemoData);
    document.getElementById('apply-report-filter-btn').addEventListener('click', refreshUI);
    document.getElementById('print-inventory-btn').addEventListener('click', printInventory);
    document.getElementById('print-report-btn').addEventListener('click', printSalesReport);
    document.getElementById('printer-type').addEventListener('change', handlePrinterTypeChange);
    document.getElementById('printer-save-btn').addEventListener('click', handlePrinterSave);
    document.getElementById('printer-test-btn').addEventListener('click', handleTestPrint);
    document.getElementById('printer-bluetooth-connect').addEventListener('click', handleBluetoothConnect);
    document.getElementById('printer-bluetooth-disconnect').addEventListener('click', handleBluetoothDisconnect);
    document.getElementById('account-form').addEventListener('submit', handleSaveAccountSettings);
    document.getElementById('add-staff-form').addEventListener('submit', handleAddStaffAccount);

    // Show the auth screen right away and let the person see the page is
    // alive, rather than leaving it blank while account setup (which now
    // talks to the shared database) completes in the background.
    const loginSubmit = document.querySelector('#login-form button[type="submit"]');
    const signupSubmit = document.querySelector('#signup-form button[type="submit"]');
    const authMessage = document.getElementById('auth-message');
    const originalAuthMessage = authMessage ? authMessage.textContent : '';
    if (!isLoggedIn()) showAuthScreen('login');
    if (loginSubmit) loginSubmit.disabled = true;
    if (signupSubmit) signupSubmit.disabled = true;
    if (authMessage) authMessage.textContent = 'Setting up…';

    await ensureDefaultAccount();

    if (loginSubmit) loginSubmit.disabled = false;
    if (signupSubmit) signupSubmit.disabled = false;
    if (authMessage) authMessage.textContent = originalAuthMessage;

    if (await resolveSession()) {
        await loadOrgState();
        await showDashboard();
    } else {
        showAuthScreen('login');
    }

    await populateReportUserFilter();
    refreshUI();
    renderPrinterStatus();
}

init();

