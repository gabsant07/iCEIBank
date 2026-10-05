import { useEffect, useState } from "react";

export default function TimelinePage({ api, notify }) {
  const [events, setEvents] = useState([]);
  const load = async () => {
    try {
      setEvents(await api.timeline());
    } catch (error) {
      notify(error.message, "error");
    }
  };

  useEffect(() => {
    let active = true;

    async function loadTimeline() {
      try {
        const data = await api.timeline();
        if (active) setEvents(data);
      } catch (error) {
        if (active) notify(error.message, "error");
      }
    }

    loadTimeline();
    return () => {
      active = false;
    };
  }, [api]);

  return <div className="page-content"><div className="page-heading"><div><small>SISTEMAS DISTRIBUÍDOS</small><h1>Linha do tempo causal</h1><p>Vetores registram causalidade; a ordem visual não implica relação causal.</p></div><button className="secondary" onClick={load}>Atualizar</button></div><section className="card timeline">{events.map(event => <article key={event.id}><div className="timeline-dot">{event.timestampVetorial ? `[${event.timestampVetorial.join(", ")}]` : "Legado"}</div><div><small>AGÊNCIA {event.agencyId} · {new Date(event.wallClock).toLocaleString("pt-BR")}</small><strong>{event.type}</strong><p>{event.details}</p></div></article>)}{!events.length && <div className="empty">Nenhum evento registrado.</div>}</section></div>;
}
