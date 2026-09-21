# Plano de desenvolvimento do Resume2Portifolio v1

## Resumo

Construir uma aplicação local-first que recebe um PDF de currículo, processa o arquivo de forma assíncrona usando RabbitMQ, gera um `ResumeDocument`, cria um portfólio HTML a partir de um dos dois templates disponíveis e entrega um ZIP para download.

A v1 terá dois providers de IA:

- `fake`: retorna sempre um `ResumeDocument` mockado e determinístico;
- `ollama`: envia o texto extraído para um modelo local.

O `fake` não interpreta o texto do currículo. Ele apenas recebe o texto já extraído, valida que existe conteúdo utilizável e retorna dados mockados. Isso permite testar todo o pipeline sem depender de IA.

## Arquitetura e fluxo

```text
Frontend
  ↓
API
  ├── valida e salva o PDF localmente
  ├── cria o job no PostgreSQL
  └── publica mensagem em resume.process
          ↓
      RabbitMQ
          ↓
   Resume Worker
  ├── verifica cancelamento
  ├── extrai texto com Apache PDFBox
  ├── valida o texto
  ├── chama Fake ou Ollama
  ├── valida ResumeDocument
  ├── salva o documento estruturado
  └── marca o job como READY

O frontend busca o `ResumeDocument` salvo junto com o template selecionado e
renderiza o portfólio no navegador. Não existe um worker separado para HTML:
os templates são componentes React do frontend.
```

Cada PDF gera um job independente. Um upload individual cria um job; vários uploads geram vários jobs. Com um worker, o processamento ocorre sequencialmente. Com vários workers, jobs diferentes podem ser processados em paralelo.

## Fases de implementação

### 1. Estrutura inicial

- Criar o backend Spring Boot.
- Criar o frontend React com Vite.
- Configurar PostgreSQL, RabbitMQ e filesystem via Docker Compose.
- Definir configuração por variáveis de ambiente.
- Organizar o backend em domínio, aplicação, infraestrutura e API.

### 2. Modelo de domínio

Criar:

- `ResumeJob`;
- `ResumeJobEvent`;
- `ResumeDocument`;
- estados e transições do job;
- registros de erro;
- identificadores internos para jobs e arquivos.

O `ResumeDocument` terá:

- `schemaVersion`;
- dados básicos;
- resumo;
- experiências;
- educação;
- skills;
- idiomas;
- projetos;
- links.

Campos vazios serão permitidos no documento, mas não serão renderizados nos templates.

### 3. Persistência e armazenamento

Implementar:

```java
public interface FileStorage {
    FileId save(InputStream content);
    InputStream read(FileId id);
    void delete(FileId id);
}
```

Criar `LocalFileStorage` com:

```text
data/
├── uploads/
│   └── {fileId}.pdf
└── generated/
    └── {jobId}/
        ├── index.html
        ├── styles.css
        └── portfolio.zip
```

O PDF e o ZIP permanecerão armazenados indefinidamente, até o usuário excluir o job.

### 4. Upload e validação do PDF

Implementar upload individual:

```text
POST /api/resumes
```

O endpoint deverá:

- aceitar multipart upload;
- validar tamanho máximo;
- validar MIME type e extensão;
- ignorar o nome original para armazenamento;
- salvar o arquivo com identificador interno;
- criar o job;
- registrar o evento `RECEIVED`;
- retornar `202 Accepted` com o `jobId`;
- publicar o comando de processamento.

PDF inválido, protegido, corrompido ou acima do limite deverá gerar erro claro para o usuário.

### 5. Extração de texto

Usar Apache PDFBox.

Fluxo:

```text
PDF
 → leitura
 → extração de texto
 → normalização de espaços e quebras
 → validação do tamanho
```

Regras:

- texto vazio ou insuficiente gera falha;
- texto acima do limite gera falha;
- PDF escaneado sem camada de texto gera falha;
- OCR não fará parte da v1;
- o texto completo não deve ser colocado nos logs nem nas mensagens RabbitMQ.

### 6. Abstração da IA

Criar:

```java
public interface ResumeAIExtractor {
    ResumeDocument extract(String text);
}
```

Implementações:

```text
FakeResumeAIExtractor
OllamaResumeAIExtractor
```

O provider será selecionado por ambiente:

```env
RESUME_AI_PROVIDER=fake
RESUME_AI_MODEL=
RESUME_AI_BASE_URL=
RESUME_AI_API_KEY=
```

Com `fake`:

- validar que o texto recebido não está vazio;
- retornar um fixture mockado válido;
- não analisar o conteúdo do currículo;
- não fazer chamadas externas;
- produzir sempre o mesmo resultado para facilitar os testes.

Com `ollama`:

- enviar o texto extraído;
- solicitar resposta no formato do `ResumeDocument`;
- validar a resposta;
- aplicar timeout;
- tratar indisponibilidade, resposta inválida e falhas temporárias.

### 7. Filas e mensagens

Criar uma fila principal:

```text
resume.process
```

Mensagens conterão apenas referências e metadados:

```json
{
  "eventId": "uuid",
  "jobId": "uuid",
  "fileId": "uuid",
  "attempt": 1,
  "schemaVersion": "1.0"
}
```

A renderização do portfólio acontece no frontend. O backend expõe o
`ResumeDocument` salvo e o template selecionado em JSON.

Filas de retry e dead-letter ficam fora do escopo atual do protótipo.

Configurar:

- confirmação manual;
- prefetch;
- número máximo de tentativas e backoff ficam como evolução futura;
- nenhum PDF ou texto completo dentro das mensagens.

### 8. Estados, eventos e cancelamento

Estados de processamento:

```text
RECEIVED
QUEUED
EXTRACTING_TEXT
AI_PROCESSING
AI_COMPLETED
READY
```

Estados finais:

```text
FAILED
CANCELLED
DELETED
```

Estado temporário:

```text
CANCEL_REQUESTED
```

Cada mudança relevante deverá gerar um evento persistido em `ResumeJobEvent`.

Implementar:

```text
GET  /api/resumes/{jobId}
POST /api/resumes/{jobId}/cancel
```

Antes de cada operação relevante, o worker deverá verificar se o job foi cancelado.

Quando o cancelamento for detectado:

- descartar resultado parcial;
- não continuar para a próxima etapa;
- marcar o job como `CANCELLED`;
- não permitir retomada;
- exigir novo upload para iniciar novamente.

Se a operação atual não puder ser interrompida imediatamente, ela poderá terminar e o resultado será descartado no próximo ponto seguro.

### 9. Idempotência e retries

O sistema deverá ser seguro contra mensagens duplicadas.

Regras:

- `eventId` identifica a mensagem;
- `jobId` identifica o processamento;
- jobs já concluídos em determinada etapa não devem ser processados novamente;
- jobs cancelados ou excluídos devem ser ignorados;
- não criar dois ZIPs conflitantes;
- não assumir entrega exatamente uma vez.

Retries serão usados para erros temporários, como:

- Ollama indisponível;
- timeout;
- falha temporária no filesystem;
- falha temporária no banco;
- erro transitório no RabbitMQ.

Erros permanentes deverão marcar o job como `FAILED` e registrar uma mensagem clara.

### 10. Templates e empacotamento

Criar dois templates selecionáveis no frontend.

Cada template deverá:

- receber somente um `ResumeDocument`;
- renderizar HTML estático;
- possuir CSS separado;
- omitir campos vazios;
- não depender da IA;
- não processar PDF;
- não incluir imagens ou assets na v1.

Formato final:

```text
portfolio.zip
├── index.html
└── styles.css
```

O template escolhido deverá ser salvo como parte da configuração do job para que a geração seja determinística.

### 11. Download, listagem e exclusão

Implementar:

```text
GET    /api/resumes/{jobId}/download
GET    /api/resumes
DELETE /api/resumes/{jobId}
```

A listagem deverá:

- usar paginação;
- retornar 10 jobs por página;
- ordenar pelos mais recentes;
- incluir status, datas, erros e disponibilidade do download;
- incluir histórico resumido de eventos quando necessário para a timeline.

O download só estará disponível para jobs `READY`.

Ao excluir:

- remover o PDF;
- remover o ZIP;
- remover arquivos gerados;
- remover ou invalidar dados associados;
- impedir que mensagens antigas recriem o processamento;
- marcar ou remover definitivamente o job conforme a estratégia de persistência escolhida.

Jobs `CANCELLED` e `FAILED` permanecerão visíveis até serem excluídos.

### 12. Frontend

Criar uma single-page application com:

- formulário de upload individual;
- seleção de template;
- tabela/lista de portfólios;
- paginação de 10 itens;
- status atual;
- timeline de eventos;
- mensagem de erro;
- botão de cancelamento;
- botão de download;
- botão de exclusão.

Usar polling enquanto existirem jobs em estados ativos. O frontend não deverá acessar diretamente RabbitMQ, PostgreSQL, filesystem ou chaves de IA.

A existência de múltiplos workers poderá ser observada pela atualização simultânea de diferentes linhas da tabela.

### 13. Testes

Cobrir:

- upload válido;
- upload inválido;
- PDF sem texto;
- PDF acima do limite;
- extração com PDFBox;
- fake retornando fixture;
- validação do `ResumeDocument`;
- geração dos dois templates;
- criação e leitura do ZIP;
- upload de vários PDFs;
- processamento sequencial com um worker;
- processamento concorrente com múltiplos workers;
- mensagem duplicada;
- worker reiniciado durante processamento;
- retry de erro temporário;
- envio para DLQ;
- cancelamento antes da extração;
- cancelamento durante IA;
- cancelamento antes da geração HTML;
- exclusão de job cancelado;
- exclusão de job pronto;
- download indisponível antes de `READY`;
- mensagem atrasada para job excluído.

O CI usará o provider `fake`, sem chamadas externas e sem dependência do Ollama.

### 14. Docker Compose e documentação

O README deverá documentar:

- arquitetura;
- fluxo dos jobs;
- execução com `fake`;
- instalação e configuração do Ollama;
- configuração do modelo;
- configuração do número de workers;
- diferença entre processamento sequencial e paralelo;
- retries e DLQs;
- cancelamento;
- exclusão;
- limites do PDF;
- ausência de OCR;
- ausência de imagens;
- privacidade ao usar Ollama;
- possibilidade futura de providers remotos;
- execução dos testes.

Configuração documentada inicialmente:

```env
RESUME_AI_PROVIDER=fake
RESUME_AI_MODEL=
RESUME_AI_BASE_URL=
RESUME_AI_API_KEY=
RESUME_AI_WORKERS=1
RESUME_AI_PREFETCH=1
```

## Critério de conclusão da v1

A v1 estará concluída quando for possível:

1. subir a aplicação com Docker Compose;
2. abrir o frontend;
3. enviar um PDF;
4. criar e acompanhar um job;
5. extrair o texto;
6. processar com `fake` ou Ollama;
7. validar o `ResumeDocument`;
8. gerar um dos dois templates;
9. baixar o ZIP;
10. visualizar o job na lista paginada;
11. cancelar jobs em processamento;
12. excluir jobs e seus arquivos;
13. observar retries, falhas e DLQs;
14. processar múltiplos currículos sequencialmente ou em paralelo conforme `RESUME_AI_WORKERS`;
15. executar todos os testes sem depender de uma API externa.

## Assumptions

- O fake será exclusivamente mockado e determinístico.
- Apache PDFBox será usado para extração textual.
- OCR ficará fora da v1.
- O upload será individual.
- Vários arquivos serão representados por vários jobs.
- Não haverá TTL.
- Não haverá autenticação.
- Não haverá assets, fotos ou imagens.
- O frontend usará polling.
- O provider padrão será `fake`, para o projeto funcionar imediatamente após o clone.
