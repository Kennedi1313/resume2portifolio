import type { PortfolioDocument } from "../../main";

type Props = { document: PortfolioDocument };

export default function MinimalTemplate({ document }: Props) {
  const { basics } = document;
  const experience = document.experience ?? [];
  const projects = document.projects ?? [];
  const skills = document.skills ?? [];
  const links = basics.links ?? [];

  return (
    <main className="portfolio minimal-portfolio">
      <div className="portfolio-frame">
        <header className="portfolio-hero">
          <div>
            <span className="portfolio-kicker">
              Portfolio / {new Date().getFullYear()}
            </span>
            <h1>{basics.name}</h1>
            <p className="portfolio-headline">{basics.headline}</p>
          </div>
          <div className="portfolio-contact">
            <a href={`mailto:${basics.email}`}>{basics.email}</a>
            {basics.phone && <span>{basics.phone}</span>}
            <span>{basics.location}</span>
            {links.map((link) => (
              <a
                href={link.url}
                target="_blank"
                rel="noreferrer"
                key={link.url}
              >
                {link.label} ↗
              </a>
            ))}
          </div>
        </header>
        <div className="minimal-layout">
          {skills.length > 0 && (
            <aside>
              <section className="portfolio-section">
                <h2>Competências</h2>
                <div className="portfolio-skills">
                  {skills.map((skill) => (
                    <span key={skill}>{skill}</span>
                  ))}
                </div>
              </section>
            </aside>
          )}
          <div className="minimal-main">
            {document.summary && (
              <section className="portfolio-section portfolio-lead">
                <h2>Perfil</h2>
                <p className="portfolio-summary">{document.summary}</p>
              </section>
            )}
            {experience.length > 0 && (
              <section className="portfolio-section">
                <h2>Experiência</h2>
                {experience.map((item) => (
                  <article
                    className="portfolio-entry"
                    key={`${item.company}-${item.role}`}
                  >
                    <small>
                      {item.startDate} — {item.endDate ?? "Atual"}
                    </small>
                    <strong>{item.role}</strong>
                    <span>{item.company}</span>
                    {(item.description ?? []).length > 0 && (
                      <ul>
                        {item.description.map((line) => (
                          <li key={line}>{line}</li>
                        ))}
                      </ul>
                    )}
                  </article>
                ))}
              </section>
            )}
            {projects.length > 0 && (
              <section className="portfolio-section">
                <h2>Projetos</h2>
                {projects.map((project) => (
                  <article className="portfolio-project" key={project.name}>
                    <strong>{project.name}</strong>
                    <p>{project.description}</p>
                    {project.url && (
                      <a href={project.url} target="_blank" rel="noreferrer">
                        Ver projeto ↗
                      </a>
                    )}
                  </article>
                ))}
              </section>
            )}
          </div>
        </div>
        <footer className="portfolio-footer">
          <span>{basics.name}</span>
          <a href={`mailto:${basics.email}`}>Vamos conversar ↗</a>
        </footer>
      </div>
    </main>
  );
}
