import {
  ChangeEvent,
  FormEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";
import "./portfolio.css";
import EditorialTemplate from "./templates/portfolio/EditorialTemplate";
import MinimalTemplate from "./templates/portfolio/MinimalTemplate";

type EventItem = { type: string; createdAt: string };
type JobStatus =
  | "QUEUED"
  | "EXTRACTING_TEXT"
  | "AI_PROCESSING"
  | "AI_COMPLETED"
  | "READY"
  | "FAILED";
type TemplateName = "editorial" | "minimal";
type Job = {
  id: string;
  filename: string;
  status: JobStatus;
  template?: TemplateName;
  portfolioUrl?: string;
  createdAt: string;
  events: EventItem[];
};
export type PortfolioDocument = {
  basics: {
    name: string;
    headline: string;
    email: string;
    phone?: string;
    location: string;
    links?: { label: string; url: string }[];
  };
  summary: string;
  experience: {
    company: string;
    role: string;
    startDate: string;
    endDate?: string;
    description: string[];
  }[];
  skills: string[];
  projects: {
    name: string;
    description: string;
    url?: string;
    technologies?: string[];
  }[];
};

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const activeStatuses: JobStatus[] = [
  "QUEUED",
  "EXTRACTING_TEXT",
  "AI_PROCESSING",
  "AI_COMPLETED",
];
const steps = [
  ["QUEUED", "Na fila"],
  ["EXTRACTING_TEXT", "Extraindo texto"],
  ["AI_PROCESSING", "Processando IA"],
  ["AI_COMPLETED", "Estruturado"],
  ["READY", "Pronto"],
] as const;

function statusLabel(status: string) {
  return status
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/(^| )\S/g, (letter) => letter.toUpperCase());
}

function PortfolioPage({ jobId }: { jobId: string }) {
  const [portfolio, setPortfolio] = useState<{
    portfolioName: string;
    template: TemplateName;
    data: PortfolioDocument;
  } | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    fetch(`${API_URL}/api/resumes/${jobId}/portfolio`)
      .then((response) => (response.ok ? response.json() : Promise.reject()))
      .then(setPortfolio)
      .catch(() => setError(true));
  }, [jobId]);

  if (error)
    return (
      <main className="portfolio-error">
        <h1>Portfólio não encontrado</h1>
        <a href="/">Voltar para o workspace</a>
      </main>
    );
  if (!portfolio)
    return <main className="portfolio-loading">Carregando portfólio...</main>;
  return portfolio.template === "minimal" ? (
    <MinimalTemplate document={portfolio.data} />
  ) : (
    <EditorialTemplate document={portfolio.data} />
  );
}

function Dashboard() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [template, setTemplate] = useState<TemplateName>("editorial");
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const intervalRef = useRef<number | undefined>(undefined);
  const selectedJob = jobs.find((job) => job.id === selectedJobId) ?? jobs[0];
  const activeCount = jobs.filter((job) =>
    activeStatuses.includes(job.status),
  ).length;

  useEffect(() => {
    fetch(`${API_URL}/api/resumes/`)
      .then((response) =>
        response.ok ? (response.json() as Promise<Job[]>) : Promise.reject(),
      )
      .then(setJobs)
      .catch(() => setError("Não foi possível carregar os portfólios salvos."));
  }, []);

  const refreshJob = async (id: string) => {
    const response = await fetch(`${API_URL}/api/resumes/${id}`);
    if (!response.ok) return;
    const updated = (await response.json()) as Job;
    setJobs((current) => current.map((job) => (job.id === id ? updated : job)));
  };

  const deleteJob = async (id: string) => {
    if (!window.confirm("Excluir este portfólio e o PDF salvo?")) return;
    const response = await fetch(`${API_URL}/api/resumes/${id}`, {
      method: "DELETE",
    });
    if (!response.ok) {
      setError("Não foi possível excluir o portfólio.");
      return;
    }
    setJobs((current) => current.filter((job) => job.id !== id));
    setSelectedJobId((current) => (current === id ? null : current));
  };

  useEffect(() => {
    if (intervalRef.current) window.clearInterval(intervalRef.current);
    if (!activeCount) return;
    intervalRef.current = window.setInterval(() => {
      jobs
        .filter((job) => activeStatuses.includes(job.status))
        .forEach((job) => void refreshJob(job.id));
    }, 1000);
    return () => {
      if (intervalRef.current) window.clearInterval(intervalRef.current);
    };
  }, [activeCount, jobs.map((job) => `${job.id}:${job.status}`).join("|")]);

  const handleFile = (event: ChangeEvent<HTMLInputElement>) => {
    setFile(event.target.files?.[0] ?? null);
    setError("");
  };

  const upload = async (event: FormEvent) => {
    event.preventDefault();
    if (!file) {
      setError("Escolha um arquivo para começar.");
      return;
    }
    setUploading(true);
    setError("");
    const body = new FormData();
    body.append("file", file);
    body.append("template", template);
    try {
      const response = await fetch(`${API_URL}/api/resumes`, {
        method: "POST",
        body,
      });
      if (!response.ok) throw new Error("Não foi possível enviar o currículo.");
      const { jobId } = (await response.json()) as { jobId: string };
      const job: Job = {
        id: jobId,
        filename: file.name,
        status: "QUEUED",
        template,
        createdAt: new Date().toISOString(),
        events: [],
      };
      setJobs((current) => [job, ...current]);
      setSelectedJobId(jobId);
      setFile(null);
      const input = document.getElementById(
        "resume-file",
      ) as HTMLInputElement | null;
      if (input) input.value = "";
    } catch (uploadError) {
      setError(
        uploadError instanceof Error
          ? uploadError.message
          : "Erro inesperado no upload.",
      );
    } finally {
      setUploading(false);
    }
  };

  const selectedStep = useMemo(
    () =>
      selectedJob
        ? steps.findIndex(([status]) => status === selectedJob.status)
        : -1,
    [selectedJob],
  );

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">R</span>
          <span>
            Resume<span className="accent">2</span>Portifolio
          </span>
        </div>
        <div className="system-status">
          <span className="status-dot" /> Local processing{" "}
          <span className="separator">·</span> {activeCount} active
        </div>
      </header>
      <main className="page-content">
        <section className="hero">
          <div>
            <p className="eyebrow">Resume to portfolio pipeline</p>
            <h1>Transforme seu currículo em uma presença digital.</h1>
            <p className="hero-copy">
              Envie um PDF e acompanhe cada etapa enquanto a aplicação estrutura
              seus dados e prepara seu portfólio.
            </p>
          </div>
          <div className="hero-orbit">
            <span>PDF</span>
            <span>AI</span>
            <span>HTML</span>
          </div>
        </section>
        <section className="upload-card">
          <form onSubmit={upload}>
            <label className="dropzone" htmlFor="resume-file">
              <span className="upload-icon">↑</span>
              <span className="drop-title">
                {file ? file.name : "Escolha seu currículo para começar"}
              </span>
              <span className="drop-caption">PDF recomendado · até 10 MB</span>
              <input
                id="resume-file"
                type="file"
                accept=".pdf,application/pdf"
                onChange={handleFile}
              />
            </label>
            <div className="template-picker">
              <span className="picker-label">Escolha um template</span>
              <div className="template-options">
                {(["editorial", "minimal"] as TemplateName[]).map((option) => (
                  <label
                    className={`template-option ${template === option ? "selected" : ""}`}
                    key={option}
                  >
                    <input
                      type="radio"
                      name="template"
                      checked={template === option}
                      onChange={() => setTemplate(option)}
                    />
                    <span>
                      <strong>
                        {option === "editorial" ? "Editorial" : "Minimal"}
                      </strong>
                      <small>
                        {option === "editorial"
                          ? "Tipografia e destaque visual"
                          : "Limpo e direto ao ponto"}
                      </small>
                    </span>
                  </label>
                ))}
              </div>
            </div>
            <div className="upload-actions">
              <span className="privacy-note">Processamento local-first.</span>
              <button className="primary-button" disabled={!file || uploading}>
                {uploading ? "Enviando…" : "Iniciar processamento"}
              </button>
            </div>
          </form>
          {error && <p className="error-message">{error}</p>}
        </section>
        <section className="workspace-grid">
          <div className="jobs-panel panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Seu workspace</p>
                <h2>Portfólios</h2>
              </div>
              <span className="count-badge">{jobs.length}</span>
            </div>
            {jobs.length === 0 ? (
              <div className="empty-state">
                <div className="empty-icon">✦</div>
                <h3>Nenhum portfólio ainda</h3>
                <p>
                  Envie seu primeiro currículo para acompanhar o processamento
                  aqui.
                </p>
              </div>
            ) : (
              <div className="job-list">
                {jobs.map((job) => (
                  <div
                    className={`job-row ${selectedJob?.id === job.id ? "selected" : ""}`}
                    key={job.id}
                    onClick={() => setSelectedJobId(job.id)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ")
                        setSelectedJobId(job.id);
                    }}
                  >
                    <span className="file-icon">PDF</span>
                    <span className="job-info">
                      <strong>{job.filename}</strong>
                      <small>
                        {new Date(job.createdAt).toLocaleTimeString([], {
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </small>
                    </span>
                    <span
                      className={`job-status ${job.status === "READY" ? "ready" : job.status === "FAILED" ? "failed" : "working"}`}
                    >
                      <i />
                      {statusLabel(job.status)}
                    </span>
                    <span className="chevron">›</span>
                    <button
                      className="delete-button"
                      type="button"
                      aria-label={`Excluir ${job.filename}`}
                      title="Excluir portfólio"
                      onClick={(event) => {
                        event.stopPropagation();
                        void deleteJob(job.id);
                      }}
                    >
                      🗑
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
          <div className="detail-panel panel">
            {selectedJob ? (
              <>
                <div className="panel-heading detail-heading">
                  <div>
                    <p className="eyebrow">Detalhes do processamento</p>
                    <h2>{selectedJob.filename}</h2>
                    <p className="selected-template">
                      Template:{" "}
                      {selectedJob.template === "minimal"
                        ? "Minimal"
                        : "Editorial"}
                    </p>
                  </div>
                  <span
                    className={`large-status ${selectedJob.status === "READY" ? "ready" : ""}`}
                  >
                    {statusLabel(selectedJob.status)}
                  </span>
                </div>
                <div className="timeline">
                  {steps.map(([status, label], index) => (
                    <div
                      className={`timeline-step ${index < selectedStep ? "done" : ""} ${index === selectedStep ? "current" : ""}`}
                      key={status}
                    >
                      <span className="step-marker">
                        {index < selectedStep ? "✓" : index + 1}
                      </span>
                      <span>{label}</span>
                      {index < steps.length - 1 && (
                        <span className="step-line" />
                      )}
                    </div>
                  ))}
                </div>
                <div className="event-log">
                  <div className="event-log-title">Activity log</div>
                  {selectedJob.events.length === 0 ? (
                    <p className="muted">Aguardando o primeiro evento…</p>
                  ) : (
                    selectedJob.events.map((event) => (
                      <div
                        className="event"
                        key={`${event.type}-${event.createdAt}`}
                      >
                        <span className="event-check">✓</span>
                        <span>{statusLabel(event.type)}</span>
                        <time>
                          {new Date(event.createdAt).toLocaleTimeString([], {
                            hour: "2-digit",
                            minute: "2-digit",
                            second: "2-digit",
                          })}
                        </time>
                      </div>
                    ))
                  )}
                </div>
                {selectedJob.status === "READY" && (
                  <a
                    className="portfolio-link"
                    href={`/portfolio/${selectedJob.id}`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Abrir portfólio
                  </a>
                )}
              </>
            ) : (
              <div className="empty-detail">
                <span>◌</span>
                <p>Selecione um portfólio para ver o progresso.</p>
              </div>
            )}
          </div>
        </section>
      </main>
      <footer>
        <span>Resume2Portifolio</span>
        <span>Built for local-first processing</span>
      </footer>
    </div>
  );
}

function App() {
  const portfolioMatch = window.location.pathname.match(
    /^\/portfolio\/([^/]+)\/?$/,
  );
  return portfolioMatch ? (
    <PortfolioPage jobId={portfolioMatch[1]} />
  ) : (
    <Dashboard />
  );
}

export default App;

createRoot(document.getElementById("root")!).render(<App />);
