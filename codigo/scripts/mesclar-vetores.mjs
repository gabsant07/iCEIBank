import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { relation } from './vector-order.mjs';

const directory = resolve(process.argv[2] || 'data');
const events = [];
let legacy = 0;
for (let agency = 0; agency < 3; agency++) {
  const file = resolve(directory, `eventos-agencia-${agency}.jsonl`);
  if (!existsSync(file)) continue;
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/).filter(Boolean)) {
    const event = JSON.parse(line);
    if (!event.timestampVetorial) { legacy++; continue; }
    relation(event.timestampVetorial, event.timestampVetorial);
    events.push(event);
  }
}
// A soma é uma ordem topológica de exibição; a causalidade exige comparar vetores.
events.sort((a,b) => a.timestampVetorial.reduce((x,y)=>x+y,0) - b.timestampVetorial.reduce((x,y)=>x+y,0)
  || a.agencyId-b.agencyId || String(a.wallClock).localeCompare(String(b.wallClock)));
events.forEach((event,i) => console.log(`E${i} | Vetor [${event.timestampVetorial}] | Agência ${event.agencyId} | ${event.type} | ${event.details}`));
for (let i=0; i<events.length; i++) for (let j=i+1; j<events.length; j++) {
  if(events[i].agencyId === events[j].agencyId) continue;
  console.log(`E${i} / E${j}: ${relation(events[i].timestampVetorial, events[j].timestampVetorial)}`);
}
if (legacy) console.warn(`${legacy} eventos legados sem vetor excluídos da análise causal.`);
if (!events.length) console.log('Nenhum evento vetorial encontrado.');
