// Local-only TCP proxy in front of the demo validator RPC, so an outage drill can cut the
// Gateway's RPC link without killing a single-node validator (which can stall its fork choice).
import net from 'node:net';
const [listen = '9133', target = '9123'] = process.argv.slice(2);
net.createServer(client => {
  const upstream = net.connect(Number(target), '127.0.0.1');
  client.pipe(upstream).pipe(client);
  const close = () => { client.destroy(); upstream.destroy(); };
  client.on('error', close); upstream.on('error', close);
}).listen(Number(listen), '127.0.0.1');
