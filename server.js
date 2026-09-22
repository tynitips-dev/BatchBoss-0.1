const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const PUBLIC_DIR = path.join(__dirname, 'public');
const DATA_FILE = path.join(__dirname, 'public', 'data', 'bakery_store.json');

// Ensure data directory exists
const dataDir = path.dirname(DATA_FILE);
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
}

// In-memory data store with disk persistence
let store = {
  bakeries: {
    "bakery_1": {
      profile: {
        bakeryId: "bakery_1",
        bakeryName: "Artisan Sweet Delights",
        fullName: "Head Baker",
        email: "orders@bakery.com",
        phone: "+27 82 555 1234",
        city: "Cape Town",
        operatingModel: "Artisan Kitchen",
        currency: "ZAR (R)",
        specialty: "Cakes & Confections"
      },
      recipes: [],
      customers: []
    }
  }
};

// Load existing data if available
try {
  if (fs.existsSync(DATA_FILE)) {
    const raw = fs.readFileSync(DATA_FILE, 'utf8');
    store = JSON.parse(raw);
    console.log('[Server] Loaded bakery data from disk.');
  }
} catch (e) {
  console.warn('[Server] Could not load initial data file, using default store:', e.message);
}

function saveStore() {
  try {
    fs.writeFileSync(DATA_FILE, JSON.stringify(store, null, 2), 'utf8');
  } catch (err) {
    console.error('[Server] Failed to persist data store:', err.message);
  }
}

function getBakery(bakeryId) {
  const cleanId = (bakeryId || 'bakery_1').trim().toLowerCase();
  if (!store.bakeries[cleanId]) {
    store.bakeries[cleanId] = {
      profile: {
        bakeryId: cleanId,
        bakeryName: "My Bakery",
        email: "",
        phone: "",
        currency: "ZAR (R)"
      },
      recipes: [],
      customers: []
    };
    saveStore();
  }
  return store.bakeries[cleanId];
}

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Requested-With'
  });
  res.end(JSON.stringify(data));
}

function readJsonBody(req, callback) {
  let body = '';
  req.on('data', chunk => {
    body += chunk.toString();
  });
  req.on('end', () => {
    try {
      const parsed = body.length > 0 ? JSON.parse(body) : {};
      callback(null, parsed);
    } catch (e) {
      callback(e, null);
    }
  });
}

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
};

const server = http.createServer((req, res) => {
  const urlObj = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = urlObj.pathname;
  const method = req.method.toUpperCase();

  // CORS preflight
  if (method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Requested-With'
    });
    return res.end();
  }

  // 1. Health check
  if (pathname === '/api/health') {
    return sendJson(res, 200, {
      status: 'ok',
      service: 'BatchBoss Web Backend',
      version: '1.0.0',
      timestamp: Date.now()
    });
  }

  // 2. List all active bakeries
  if (pathname === '/api/bakeries') {
    const list = Object.keys(store.bakeries).map(id => ({
      bakeryId: id,
      bakeryName: store.bakeries[id].profile?.bakeryName || id,
      recipesCount: store.bakeries[id].recipes?.length || 0,
      customersCount: store.bakeries[id].customers?.length || 0
    }));
    return sendJson(res, 200, list);
  }

  // 3. Bakery routes: /api/bakery/:bakeryId/...
  const bakeryMatch = pathname.match(/^\/api\/bakery\/([^\/]+)(?:\/(.*))?$/);
  if (bakeryMatch) {
    const bakeryId = decodeURIComponent(bakeryMatch[1]).toLowerCase();
    const subRoute = (bakeryMatch[2] || '').toLowerCase();
    const bakery = getBakery(bakeryId);

    // GET /api/bakery/:bakeryId or /api/bakery/:bakeryId/all or /api/bakery/:bakeryId/summary
    if (subRoute === '' || subRoute === 'all' || subRoute === 'summary') {
      return sendJson(res, 200, {
        bakeryId: bakeryId,
        profile: bakery.profile,
        recipes: bakery.recipes,
        customers: bakery.customers
      });
    }

    // Profile: /api/bakery/:bakeryId/profile
    if (subRoute === 'profile') {
      if (method === 'GET') {
        return sendJson(res, 200, bakery.profile || {});
      }
      if (method === 'POST' || method === 'PUT') {
        return readJsonBody(req, (err, data) => {
          if (err) return sendJson(res, 400, { error: 'Invalid JSON payload' });
          bakery.profile = { ...(bakery.profile || {}), ...data, bakeryId: bakeryId };
          saveStore();
          console.log(`[Server] Updated profile for bakery: ${bakeryId}`);
          return sendJson(res, 200, { success: true, profile: bakery.profile });
        });
      }
    }

    // Recipes: /api/bakery/:bakeryId/recipes
    if (subRoute === 'recipes') {
      if (method === 'GET') {
        return sendJson(res, 200, bakery.recipes || []);
      }
      if (method === 'POST') {
        return readJsonBody(req, (err, data) => {
          if (err) return sendJson(res, 400, { error: 'Invalid JSON payload' });
          if (!data || !data.name) return sendJson(res, 400, { error: 'Recipe name is required' });

          data.bakeryId = bakeryId;
          data.updatedAt = Date.now();

          // Upsert by id or name
          const idx = bakery.recipes.findIndex(r => (data.id && r.id === data.id) || (r.name && r.name.toLowerCase() === data.name.toLowerCase()));
          if (idx >= 0) {
            bakery.recipes[idx] = { ...bakery.recipes[idx], ...data };
          } else {
            if (!data.id) data.id = Date.now();
            bakery.recipes.push(data);
          }
          saveStore();
          console.log(`[Server] Saved recipe '${data.name}' for bakery: ${bakeryId}`);
          return sendJson(res, 200, { success: true, recipe: data });
        });
      }
    }

    // Customers: /api/bakery/:bakeryId/customers
    if (subRoute === 'customers') {
      if (method === 'GET') {
        return sendJson(res, 200, bakery.customers || []);
      }
      if (method === 'POST') {
        return readJsonBody(req, (err, data) => {
          if (err) return sendJson(res, 400, { error: 'Invalid JSON payload' });
          if (!data || !data.name) return sendJson(res, 400, { error: 'Customer name is required' });

          data.bakeryId = bakeryId;
          data.updatedAt = Date.now();

          // Upsert by id or name
          const idx = bakery.customers.findIndex(c => (data.id && c.id === data.id) || (c.name && c.name.toLowerCase() === data.name.toLowerCase()));
          if (idx >= 0) {
            bakery.customers[idx] = { ...bakery.customers[idx], ...data };
          } else {
            if (!data.id) data.id = Date.now();
            bakery.customers.push(data);
          }
          saveStore();
          console.log(`[Server] Saved customer '${data.name}' for bakery: ${bakeryId}`);
          return sendJson(res, 200, { success: true, customer: data });
        });
      }
    }

    return sendJson(res, 404, { error: `Subroute '${subRoute}' not found for bakery '${bakeryId}'` });
  }

  // 4. Static file server
  let safePath = path.normalize(pathname).replace(/^(\.\.[\/\\])+/, '');
  if (safePath === '/' || safePath === '') safePath = '/index.html';

  let filePath = path.join(PUBLIC_DIR, safePath);

  // If path doesn't exist, try appending .html or falling back to index.html
  if (!fs.existsSync(filePath)) {
    if (fs.existsSync(filePath + '.html')) {
      filePath = filePath + '.html';
    } else {
      filePath = path.join(PUBLIC_DIR, 'index.html');
    }
  }

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      return res.end('404 Not Found');
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    res.writeHead(200, {
      'Content-Type': contentType,
      'Access-Control-Allow-Origin': '*'
    });
    fs.createReadStream(filePath).pipe(res);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[BatchBoss Web Backend] Running at http://0.0.0.0:${PORT}`);
  console.log(`[BatchBoss Web Backend] Serving static assets from ${PUBLIC_DIR}`);
});
