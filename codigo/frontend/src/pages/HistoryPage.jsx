import { useState } from "react";
import Field from "../components/Field";

const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export default function HistoryPage({ api, notify }) {
  const [account, setAccount] = useState("");
  const [items, setItems] = useState([]);
  async function search(event) { event.preventDefault(); try { setItems(await api.history(account)); } catch (error) { notify(error.message, "error"); } }
  return <div className="page-content"><div className="page-heading"><div><small>FUNCIONALIDADE ADICIONAL</small><h1>Histórico da conta</h1><p>Consulte as movimentações registradas localmente.</p></div></div><section className="card"><form className="inline-form" onSubmit={search}><Field label="Número da conta" type="number" required value={account} onChange={e => setAccount(e.target.value)} /><button className="primary">Consultar</button></form></section><section className="card table-card"><table><thead><tr><th>Data</th><th>Tipo</th><th>Origem</th><th>Destino</th><th>Valor</th><th>Status</th><th>Vetor</th></tr></thead><tbody>{items.map(item => <tr key={item.id}><td>{new Date(item.createdAt).toLocaleString("pt-BR")}</td><td>{item.type}</td><td>{item.sourceAccount ?? "—"}</td><td>{item.destinationAccount ?? "—"}</td><td className="money">{money.format(item.amount || 0)}</td><td><span className={`badge ${item.status?.toLowerCase()}`}>{item.status}</span></td><td>{item.timestampVetorial ? `[${item.timestampVetorial.join(", ")}]` : "Legado"}</td></tr>)}{!items.length && <tr><td colSpan="7" className="empty">Informe uma conta para consultar o histórico.</td></tr>}</tbody></table></section></div>;
}
