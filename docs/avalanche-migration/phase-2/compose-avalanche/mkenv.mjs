// Throwaway env file for the LOCAL_SIMULATION compose run: the placeholders of .env.example plus random disposable secrets and the local
// Avalanche settings. It never reads the real .env. Usage: node mkenv.mjs <output-file>
import { readFileSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', '..', '..');
const rnd = n => randomBytes(n).toString('hex');
const vars = new Map();
for (const line of readFileSync(join(root, '.env.example'), 'utf8').split(/\r?\n/)) {
  const m = /^([A-Z][A-Z0-9_]*)=(.*)$/.exec(line);
  if (m) vars.set(m[1], m[2]);
}
// passwords: 24 to 72 characters (bcrypt); secrets and keys: at least 64 characters for the JWT secrets (HS512)
for (const k of ['DEMO_CLIENT_PASSWORD', 'DEMO_FREELANCER_PASSWORD', 'DEMO_ADMIN_PASSWORD', 'PAYMENT_DEMO_USER_PASSWORD', 'MISA_PLATFORM_ACCOUNT_PASSWORD']) vars.set(k, 'throwaway-' + rnd(16));
for (const k of ['PAYMENT_JWT_SECRET', 'MISA_JWT_SECRET', 'MARKETPLACE_JWT_SECRET', 'MYSQL_ROOT_PASSWORD', 'PAYMENT_INTERNAL_API_KEY', 'SOLANA_INTERNAL_API_KEY', 'AVALANCHE_INTERNAL_API_KEY']) vars.set(k, 'throwaway-' + rnd(32));
vars.set('MISA_PLATFORM_ACCOUNT_EMAIL', 'throwaway@example.test');
vars.set('VITE_AVALANCHE_ENABLED', 'true');
vars.set('PARTNER_MOCK_E2E_NEGOTIATION_SECONDS', '5');
// local Avalanche gateway and marketplace rail
vars.set('AVALANCHE_ENVIRONMENT', 'LOCAL_SIMULATION');
vars.set('AVALANCHE_RPC_PRIMARY_URL', 'http://anvil:8545');
vars.set('AVALANCHE_RPC_MIN_GAP_MS', '0');
vars.set('AVALANCHE_SIGNERS_ENABLED', 'true');
vars.set('AVALANCHE_TX_ENABLED', 'true');
vars.set('AVALANCHE_DB_URL', 'jdbc:mysql://mysql:3306/avalanche_gateway');
vars.set('AVALANCHE_DB_USERNAME', 'root');
vars.set('AVALANCHE_DB_PASSWORD', vars.get('MYSQL_ROOT_PASSWORD'));
vars.set('AVALANCHE_TX_RECONCILE_INTERVAL_MS', '500');
vars.set('AVALANCHE_DEPLOYMENT_MANIFEST', '/deployments/LOCAL_SIMULATION.json');
vars.set('AVALANCHE_GATEWAY_ENABLED', 'true');
vars.set('AVALANCHE_GATEWAY_SETTLEMENT_NOTIFICATIONS', 'true');
vars.set('AVALANCHE_PAYOUT_ENABLED', 'true');
vars.set('AVALANCHE_PAYOUT_PARTNER', 'local-mock');
writeFileSync(process.argv[2], [...vars].map(([k, v]) => `${k}=${v}`).join('\n') + '\n');
console.log('written', vars.size, 'variables');
