import { useEffect, useMemo, useState } from "react";

const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export default function OverviewPage({ api, session, onNavigate }) {
  const [accounts, setAccounts] = useState([]);
  const [status, setStatus] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadOverview() {
      try {
        const [accountData, statusData] = await Promise.all([api.listAccounts(), api.currentAgency()]);
        if (active) {
          setAccounts(accountData);
          setStatus(statusData);
        }
      } catch (exception) {
        if (active) setError(exception.message);
      }
    }

    loadOverview();
    return () => {
      active = false;
    };
  }, [api]);

  const total = useMemo(() => accounts.reduce((sum, account) => sum + Number(account.balance), 0), [accounts]);

  return (
    <div className="page-content">
      <div className="page-heading"><div><small>PAINEL PRINCIPAL</small><h1>Olá, administrador</h1><p>Acompanhe a situação da {session.label}.</p></div><span className="status-pill">● Agência online</span></div>
      {error && <div className="form-error">{error}</div>}
      <div className="stats-grid">
        <article className="stat-card featured"><small>Saldo total da agência</small><strong>{money.format(total)}</strong><span>{accounts.length} contas cadastradas</span></article>
        <article className="stat-card"><small>Relógio lógico</small><strong>{status?.timestampVetorial ? `[${status.timestampVetorial.join(", ")}]` : "—"}</strong><span>Relógio vetorial atual</span></article>
        <article className="stat-card"><small>Agência atual</small><strong>0{session.id}</strong><span>{session.name}</span></article>
      </div>
      <section className="card">
        <div className="section-heading"><div><h2>Ações rápidas</h2><p>Escolha uma operação para continuar.</p></div></div>
        <div className="quick-grid">
          <button onClick={() => onNavigate("accounts")}><strong>Nova conta</strong><span>Cadastre uma conta nesta agência</span></button>
          <button onClick={() => onNavigate("movement")}><strong>Movimentar saldo</strong><span>Faça um depósito ou saque</span></button>
          <button onClick={() => onNavigate("transfer")}><strong>Nova transferência</strong><span>Transfira entre contas e agências</span></button>
          <button onClick={() => onNavigate("timeline")}><strong>Ver eventos</strong><span>Consulte a ordem lógica da rede</span></button>
        </div>
      </section>
    </div>
  );
}
