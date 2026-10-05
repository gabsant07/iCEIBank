import { useState } from "react";
import Field from "../components/Field";
import { agencyForAccount } from "../config/agencies";

export default function TransferPage({ api, session, notify }) {
  const [form, setForm] = useState({ sourceAccount: "", destinationAccount: "", amount: "" });
  const [result, setResult] = useState(null);
  const destination = form.destinationAccount ? agencyForAccount(form.destinationAccount) : null;

  async function submit(event) {
    event.preventDefault();
    try {
      const response = await api.transfer({ sourceAccount: Number(form.sourceAccount), destinationAccount: Number(form.destinationAccount), amount: Number(form.amount) });
      setResult(response);
      notify("Transferência registrada. Consulte o status e o histórico do destino.");
    } catch (error) { notify(error.message, "error"); }
  }

  return <div className="page-content narrow"><div className="page-heading"><div><small>TRANSFERÊNCIAS</small><h1>Nova transferência</h1><p>Transferências remotas são publicadas no RabbitMQ e processadas pelo destino de forma assíncrona.</p></div></div><section className="card operation-card"><div className="transfer-route"><span>A{session.id}</span><div>──────→</div><span>{destination ? `A${destination.id}` : "?"}</span></div><form onSubmit={submit} className="form-stack"><Field label="Conta de origem" type="number" required value={form.sourceAccount} onChange={e => setForm({ ...form, sourceAccount: e.target.value })} hint={`Deve pertencer à Agência ${session.id}.`} /><Field label="Conta de destino" type="number" required value={form.destinationAccount} onChange={e => setForm({ ...form, destinationAccount: e.target.value })} hint={destination ? `${destination.label} · ${destination.name}` : "A agência será identificada automaticamente."} /><Field label="Valor" type="number" min="0.01" step="0.01" required value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} /><button className="primary wide">Transferir</button></form>{result && <div className="result-card"><small>Status</small><strong>{result.status}</strong><span>Vetor {result.timestampVetorial?.join(", ")}</span></div>}</section></div>;
}
