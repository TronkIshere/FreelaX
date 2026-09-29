(() => {
  "use strict";

  const STORAGE_KEY = "freelax-interactive-demo-v0.1";
  const app = document.getElementById("app");
  const JOB = {
    title: "Build Solana Payment Infrastructure",
    client: "North Studio",
    freelancer: "Assigned Freelancer",
    budget: "500 USD",
    description: "Implement settlement, reconciliation, and failure handling for a cross-border payout workflow. Deliver concise handoff notes and an integration URL."
  };
  const WORK_STEPS = ["Assigned", "Working", "Submitted", "Review", "Revision", "Resubmitted", "Approved"];
  const PHASE_RANK = {
    DISCOVERABLE: 0, APPLIED: 1, IN_PROGRESS: 2, SUBMITTED_V1: 3,
    REVISION_REQUESTED: 4, SUBMITTED_V2: 5, COMPLETED: 6,
    FINANCIAL_PROCESSING: 7, FINANCIAL_SIMULATED: 8, FINANCIAL_COMPLETE: 9
  };
  const FINANCIAL_EVENTS = [
    { title: "Checkout captured", status: "CAPTURED", detail: "500 USD for approved work. Payment-backend checkout capture.", proof: "FIXTURE-CHECKOUT-001" },
    { title: "Payout record", status: "RECORD CREATED", detail: "Post-approval payout processing begins. Simulation = true.", proof: "500 USD source · 5 USD fee · 495 USD net" },
    { title: "Mock on-ramp", status: "CONFIRMED", detail: "495.00 Mock USDC, not a real fiat purchase.", proof: "FIXTURE-PURCHASE-001 · FIXTURE-SIG-ONRAMP-001" },
    { title: "Client payment evidence", status: "CONFIRMED", detail: "Rate, invoice, and on-chain payment records are separate from checkout.", proof: "FIXTURE-RATE-001 · FIXTURE-INVOICE-001 · FIXTURE-SIG-PAYMENT-001" },
    { title: "Off-ramp request", status: "CONFIRMED", detail: "DEVNET withdrawal request evidence; no fiat disbursement claim.", proof: "FIXTURE-WITHDRAWAL-001 · FIXTURE-SIG-WITHDRAWAL-001" },
    { title: "Simulated VND payout", status: "SIMULATED", detail: "12,300,000 VND dự kiến after fee. No real bank transfer.", proof: "TECHCOMBANK · ******6789 · FIXTURE-OFFRAMP-001" },
    { title: "Completion evidence", status: "COMPLETED", detail: "Simulated/on-chain completion record only; the VND amount remains estimated.", proof: "FIXTURE-SIG-COMPLETION-001" },
    { title: "Tax record", status: "DRAFT", detail: "12,600,000 VND taxable income. Certificate not issued.", proof: "FIXTURE-TAX-001 · LIVE_OPEN_ER_API" }
  ];
  const DEFAULT_FEEDBACK = "Please tighten the handoff notes and include the final integration URL.";
  const DEFAULT_V1 = "Implemented the settlement flow, reconciliation handling, and initial failure recovery path.";
  const DEFAULT_V2 = "Tightened the handoff notes and documented the final integration and reconciliation path.";

  function initialState() {
    return {
      phase: "DISCOVERABLE", role: "FREELANCER", view: "discover", financialStage: 0,
      v1Summary: DEFAULT_V1, v1Url: "https://example.invalid/freelax-demo/v1",
      feedback: "", v2Summary: DEFAULT_V2, v2Url: "https://example.invalid/freelax-demo/v2",
      approvedVersion: 0, notice: ""
    };
  }

  let storageAvailable = true;
  function readStored() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return initialState();
      const parsed = JSON.parse(raw);
      if (!parsed || !(parsed.phase in PHASE_RANK)) return initialState();
      const base = initialState();
      const next = { ...base, ...parsed };
      if (!["CLIENT", "FREELANCER"].includes(next.role)) next.role = base.role;
      if (typeof next.view !== "string") next.view = base.view;
      next.financialStage = Math.max(0, Math.min(8, Number(next.financialStage) || 0));
      for (const field of ["v1Summary", "v1Url", "feedback", "v2Summary", "v2Url", "notice"]) {
        if (typeof next[field] !== "string") next[field] = base[field];
      }
      next.approvedVersion = [0, 1, 2].includes(next.approvedVersion) ? next.approvedVersion : 0;
      return next;
    } catch (_error) {
      storageAvailable = false;
      return initialState();
    }
  }
  let state = readStored();
  function save() {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); storageAvailable = true; }
    catch (_error) { storageAvailable = false; }
  }
  function rank() { return PHASE_RANK[state.phase]; }
  function atLeast(phase) { return rank() >= PHASE_RANK[phase]; }
  function completed() { return atLeast("COMPLETED"); }
  function assigned() { return atLeast("IN_PROGRESS"); }
  function jobStatus() {
    if (completed()) return "COMPLETED";
    if (["SUBMITTED_V1", "SUBMITTED_V2"].includes(state.phase)) return "SUBMITTED_FOR_REVIEW";
    if (state.phase === "REVISION_REQUESTED") return "REVISION_REQUESTED";
    if (assigned()) return "IN_PROGRESS";
    return "OPEN";
  }
  function applicationStatus() {
    if (rank() === 0) return "Chưa ứng tuyển";
    return assigned() ? "ACCEPTED" : "PENDING";
  }
  function latestVersion() { return state.phase === "SUBMITTED_V2" || completed() ? (state.approvedVersion || 2) : 1; }
  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
  }
  function safeLink(value) {
    if (!value) return "Không có URL bàn giao";
    try {
      const url = new URL(value);
      if (!["https:", "http:"].includes(url.protocol)) return esc(value);
      return `<a href="${esc(url.href)}" target="_blank" rel="noopener noreferrer">${esc(value)} ↗</a>`;
    } catch (_error) { return esc(value); }
  }
  function button(label, action, className = "button", extra = "") {
    return `<button type="button" class="${className}" data-action="${action}" ${extra}>${label}</button>`;
  }
  function pageHead(eyebrow, title, aside, intro = "") {
    return `<header class="page-head"><div><div class="eyebrow">${eyebrow}</div><h1>${title}</h1>${intro ? `<p>${intro}</p>` : ""}</div><div class="page-aside">${aside}</div></header>`;
  }
  function notice() { return state.notice ? `<div class="notice" role="status">${esc(state.notice)}</div>` : ""; }
  function workSubnav() {
    const tabs = [["discover", "Khám phá"], ["applications", "Ứng tuyển của tôi"], ["my-work", "Công việc của tôi"]];
    const current = ["freelancer-detail"].includes(state.view) ? "discover" : ["workspace", "composer-v1"].includes(state.view) ? "my-work" : state.view;
    return `<nav class="subnav" aria-label="Các mục Công việc">${tabs.map(([view, label]) => `<button class="subnav-button" type="button" data-action="navigate" data-view="${view}" ${current === view ? 'aria-current="page"' : ""}>${label}</button>`).join("")}</nav>`;
  }
  function landingForRole(role) {
    if (role === "CLIENT") return ["SUBMITTED_V1", "SUBMITTED_V2"].includes(state.phase) ? "review" : "client-jobs";
    if (completed()) return "financial";
    return assigned() ? "workspace" : "discover";
  }
  function navItems() {
    return state.role === "CLIENT"
      ? [["Tổng quan", "overview"], ["Công việc", "client-jobs"], ["Thanh toán", "financial"], ["Hoạt động", "activity"], ["Tài khoản", "account"]]
      : [["Tổng quan", "overview"], ["Công việc", "discover"], ["Thu nhập", "financial"], ["Hoạt động", "activity"], ["Tài khoản", "account"]];
  }
  function navCategory() {
    if (["discover", "freelancer-detail", "applications", "my-work", "workspace", "composer-v1", "client-jobs", "client-job", "client-applications", "review", "feedback", "completed"].includes(state.view)) return "work";
    if (["financial", "tax"].includes(state.view)) return "financial";
    return state.view;
  }
  function currentPageLabel() {
    const labels = { overview: "Tổng quan", discover: "Công việc / Khám phá", "freelancer-detail": "Công việc / Chi tiết", applications: "Công việc / Ứng tuyển của tôi", "my-work": "Công việc / Công việc của tôi", workspace: "Công việc / Workspace", "composer-v1": "Công việc / Bàn giao V1", "client-jobs": "Công việc / Việc của bạn", "client-job": "Công việc / Chi tiết", "client-applications": "Công việc / Ứng tuyển", review: "Công việc / Duyệt bàn giao", feedback: "Công việc / Yêu cầu chỉnh sửa", completed: "Công việc / Hoàn tất", financial: state.role === "CLIENT" ? "Thanh toán / Bằng chứng" : "Thu nhập / Bằng chứng", tax: "Chứng từ thuế", activity: "Hoạt động", account: "Tài khoản" };
    return labels[state.view] || "Tổng quan";
  }
  function shell(content) {
    return `<div class="app-shell"><header class="masthead"><div><div class="logo">FREE<span>LAX.</span></div><small class="brand-note">Marketplace / work first</small></div><div class="role-switch" role="group" aria-label="Chọn vai trò trong demo"><button class="role-button" type="button" data-action="role-client" aria-pressed="${state.role === "CLIENT"}">CLIENT</button><button class="role-button" type="button" data-action="role-freelancer" aria-pressed="${state.role === "FREELANCER"}">FREELANCER</button></div><div class="edition">LOCAL DEMO<span>STATIC FIXTURE</span></div></header><nav class="primary-nav" aria-label="Điều hướng ${state.role}">${navItems().map(([label, view]) => { const category = view === "discover" || view === "client-jobs" ? "work" : view === "financial" ? "financial" : view; return `<button type="button" class="nav-button" data-action="navigate" data-view="${view}" ${navCategory() === category ? 'aria-current="page"' : ""}>${label}</button>`; }).join("")}</nav><div class="top-context"><span>${state.role} / ${esc(currentPageLabel())}</span><span>Job ${esc(jobStatus())} · Application ${esc(applicationStatus())}</span></div><main id="main-content" class="view" tabindex="-1">${notice()}${content}</main>${demoControls()}<footer class="page-footer"><span>FreelaX Interactive Demo v0.2 · D1 → D2 → D3</span><span>Static local fixture · Flutter and backend unchanged</span></footer></div>`;
  }
  function demoControls() {
    const next = completed() && state.financialStage < 8 ? button("Next financial event →", "financial-next", "button button-secondary") : "";
    return `<aside class="demo-controls" aria-label="Demo controls"><div><strong>DEMO CONTROLS · NOT PRODUCT ACTIONS</strong><p>Local simulation only · no payout, bank transfer, or certificate creation.${storageAvailable ? "" : " Local storage is unavailable; reload persistence may not work."}</p></div><div class="demo-control-actions">${next}${button("Reset demo", "reset", "button button-secondary")}</div></aside>`;
  }

  function renderDiscover() {
    const otherRows = [
      ["Audit Cross-Border Payout Records", "Review payout records and document reconciliation exceptions.", "Delta Works", "420 USD"],
      ["Design Contractor Handoff Flow", "Map the submission and review handoff for a contractor workflow.", "Atlas Studio", "360 USD"],
      ["Build Invoice Event Webhooks", "Implement invoice event delivery and retry handling for a client workflow.", "Field Office", "640 USD"]
    ];
    const featureStatus = jobStatus();
    return `${workSubnav()}${pageHead("Freelancer / Khám phá", "Tìm việc đáng làm<em>.</em>", "<strong>04</strong> công việc trong mẫu D1. Một công việc dẫn toàn bộ hành trình.", "Danh sách biên tập dày, ưu tiên công việc và hành động tiếp theo.")}${sectionHeading("Công việc đang mở", "Fixture chính + ba hàng tham chiếu D1") }<section class="job-table" aria-label="Danh sách công việc"><div class="table-head"><span>#</span><span>Công việc / mô tả</span><span>Client</span><span>Ngân sách</span><span>Trạng thái</span><span>Bước tiếp theo</span></div><article class="job-row featured"><span class="row-index">01</span><div class="row-main"><h3>${JOB.title}</h3><p>${JOB.description}</p></div><div class="cell"><span class="cell-label">Client</span>${JOB.client}</div><div class="cell"><span class="cell-label">Ngân sách</span><strong>${JOB.budget}</strong></div><div class="state-mark ${assigned() ? "mint" : ""}">${featureStatus}<br>${rank() === 0 ? "Chưa ứng tuyển" : applicationStatus()}</div><div class="row-action">${button("Mở công việc ↗", "navigate", "button", 'data-view="freelancer-detail"')}</div></article>${otherRows.map((row, index) => `<article class="job-row context"><span class="row-index">0${index + 2}</span><div class="row-main"><h3>${row[0]}</h3><p>${row[1]}</p></div><div class="cell"><span class="cell-label">Client</span>${row[2]}</div><div class="cell"><span class="cell-label">Ngân sách</span><strong>${row[3]}</strong></div><div class="state-mark">OPEN<br>Mẫu tham chiếu</div><div class="row-action"><span class="quiet-note">Chỉ xem</span></div></article>`).join("")}</section><div class="list-foot"><span>01 công việc tương tác · 03 hàng D1 để xem mật độ</span><span>Ngân sách là giá trị công việc, không phải thu nhập đã nhận.</span></div>`;
  }
  function sectionHeading(title, meta = "") { return `<div class="section-heading"><h2>${title}</h2>${meta ? `<small>${meta}</small>` : ""}</div>`; }
  function jobFacts() { return `<div class="fact-grid"><div class="fact"><span>Ngân sách · chỉ xem</span><strong>${JOB.budget}</strong></div><div class="fact"><span>Client</span><strong>${JOB.client}</strong></div><div class="fact"><span>Trạng thái công việc</span><strong>${jobStatus()}</strong></div></div>`; }
  function renderFreelancerDetail() {
    let action;
    if (state.phase === "DISCOVERABLE") action = `${button("Ứng tuyển", "apply")}`;
    else if (state.phase === "APPLIED") action = `${button("Đã ứng tuyển", "noop", "button", "disabled")}${button("Xem đơn ứng tuyển", "navigate", "button button-secondary", 'data-view="applications"')}`;
    else if (completed()) action = button("Xem bằng chứng thanh toán", "navigate", "button", 'data-view="financial"');
    else action = button("Mở workspace", "navigate", "button", 'data-view="workspace"');
    return `${workSubnav()}${pageHead("Freelancer / Chi tiết công việc", JOB.title, `<strong>${jobStatus()}</strong>${rank() === 0 ? "Có thể ứng tuyển" : applicationStatus()}`, `${JOB.client} · ${JOB.freelancer}`)}<div class="detail-grid"><article class="document"><h2>Thông tin công việc</h2><p>${JOB.description}</p>${jobFacts()}<h2>Phạm vi bàn giao</h2><p>Settlement integration, reconciliation notes, failure handling, and a final integration reference. The job remains the organizing object for work and later financial evidence.</p></article><aside class="action-panel"><div class="eyebrow">Freelancer / bước tiếp theo</div><h2>${state.phase === "DISCOVERABLE" ? "Gửi đơn ứng tuyển" : completed() ? "Xem hồ sơ đã duyệt" : assigned() ? "Theo dõi công việc" : "Đơn đã gửi"}</h2><p>${state.phase === "DISCOVERABLE" ? "Ứng tuyển cho công việc OPEN này." : state.phase === "APPLIED" ? "Đơn đang PENDING. Client chưa gán công việc." : completed() ? "Công việc đã duyệt; trạng thái tài chính là luồng riêng." : "Bạn được gán; mở workspace để xem hành động hiện tại."}</p>${action}<p class="quiet-note">Không có thao tác sửa ngân sách trong demo.</p></aside></div>`;
  }
  function renderApplications() {
    if (state.phase === "DISCOVERABLE") return `${workSubnav()}${pageHead("Freelancer / Ứng tuyển", "Ứng tuyển của tôi", "<strong>00</strong> đơn trong fixture")}${empty("Chưa có đơn ứng tuyển", "Mở công việc đang OPEN để bắt đầu hành trình.", "Khám phá công việc", "discover")}`;
    return `${workSubnav()}${pageHead("Freelancer / Ứng tuyển", "Ứng tuyển của tôi", `<strong>01</strong> đơn · ${applicationStatus()}`)}${sectionHeading("Hồ sơ ứng tuyển", "Trạng thái riêng với trạng thái Job")}<div class="job-table"><div class="table-head"><span>#</span><span>Công việc</span><span>Client</span><span>Ngân sách</span><span>Đơn của bạn</span><span>Bước tiếp theo</span></div><article class="job-row featured"><span class="row-index">01</span><div class="row-main"><h3>${JOB.title}</h3><p>${JOB.description}</p></div><div class="cell">${JOB.client}</div><div class="cell"><strong>${JOB.budget}</strong></div><div class="state-mark ${assigned() ? "mint" : ""}">${applicationStatus()}<br>${assigned() ? "Đã được chọn" : "Đang chờ Client"}</div><div class="row-action">${button(assigned() ? "Mở workspace ↗" : "Xem công việc ↗", "navigate", "button", `data-view="${assigned() ? "workspace" : "freelancer-detail"}"`)}</div></article></div>`;
  }
  function renderMyWork() {
    if (!assigned()) return `${workSubnav()}${pageHead("Freelancer / Công việc của tôi", "Công việc của tôi", "<strong>00</strong> công việc được gán")}${empty("No active assigned job yet.", "Client cần chọn Freelancer từ danh sách ứng tuyển trước khi workspace mở.", "Xem đơn ứng tuyển", "applications")}`;
    return `${workSubnav()}${pageHead("Freelancer / Công việc của tôi", "Công việc của tôi", `<strong>01</strong> công việc · ${jobStatus()}`)}${sectionHeading("Công việc được gán", "Chủ sở hữu hành động theo trạng thái")}<div class="job-table"><div class="table-head"><span>#</span><span>Công việc</span><span>Client</span><span>Ngân sách</span><span>Trạng thái</span><span>Bước tiếp theo</span></div><article class="job-row featured"><span class="row-index">01</span><div class="row-main"><h3>${JOB.title}</h3><p>${JOB.description}</p></div><div class="cell">${JOB.client}</div><div class="cell"><strong>${JOB.budget}</strong></div><div class="state-mark mint">${jobStatus()}</div><div class="row-action">${button(completed() ? "Xem hồ sơ ↗" : "Mở workspace ↗", "navigate", "button", 'data-view="workspace"')}</div></article></div>`;
  }
  function renderClientJobs() {
    return `${pageHead("Client / Công việc", "Việc nào cần bạn<em>?</em>", `<strong>01</strong> công việc fixture<br>${jobStatus()}`, "Một hồ sơ xuyên suốt ứng tuyển, bàn giao và thanh toán.")}${sectionHeading("Việc của bạn", "Ngân sách chỉ xem")}<div class="job-table"><div class="table-head"><span>#</span><span>Công việc / mô tả</span><span>Freelancer</span><span>Ngân sách</span><span>Trạng thái</span><span>Bước tiếp theo</span></div><article class="job-row featured"><span class="row-index">01</span><div class="row-main"><h3>${JOB.title}</h3><p>${JOB.description}</p></div><div class="cell"><span class="cell-label">Freelancer</span>${assigned() ? JOB.freelancer : "Chưa gán"}</div><div class="cell"><span class="cell-label">Ngân sách</span><strong>${JOB.budget}</strong></div><div class="state-mark ${["SUBMITTED_V1", "SUBMITTED_V2"].includes(state.phase) ? "verm" : assigned() ? "mint" : ""}">${jobStatus()}<br>${["SUBMITTED_V1", "SUBMITTED_V2"].includes(state.phase) ? "Client cần duyệt" : completed() ? "Công việc đã duyệt" : assigned() ? "Freelancer đang làm" : "Đang nhận ứng tuyển"}</div><div class="row-action">${button("Mở công việc ↗", "navigate", "button", 'data-view="client-job"')}</div></article></div>`;
  }
  function renderClientJob() {
    let action;
    if (!assigned()) action = button("Xem ứng tuyển", "navigate", "button", 'data-view="client-applications"');
    else if (["SUBMITTED_V1", "SUBMITTED_V2"].includes(state.phase)) action = button("Duyệt bàn giao", "navigate", "button", 'data-view="review"');
    else if (completed()) action = button("Xem bằng chứng thanh toán", "navigate", "button", 'data-view="financial"');
    else action = `<p class="quiet-note">Freelancer đang sở hữu bước bàn giao tiếp theo.</p>`;
    return `${pageHead("Client / Chi tiết công việc", JOB.title, `<strong>${jobStatus()}</strong>${assigned() ? "Freelancer: " + JOB.freelancer : "Đang nhận ứng tuyển"}`, `${JOB.client} · ${JOB.budget}`)}<div class="detail-grid"><article class="document"><h2>Thông tin công việc</h2><p>${JOB.description}</p>${jobFacts()}${sectionHeading("Ứng tuyển", rank() === 0 ? "Chưa có đơn" : assigned() ? "01 đơn được chấp nhận" : "01 đơn đang chờ")}<p>${rank() === 0 ? "Chưa có đơn ứng tuyển cho fixture job." : `${JOB.freelancer} · ${applicationStatus()}`}</p></article><aside class="action-panel"><div class="eyebrow">Client / bước tiếp theo</div><h2>${!assigned() ? "Xem đơn ứng tuyển" : completed() ? "Xem hồ sơ tài chính" : ["SUBMITTED_V1", "SUBMITTED_V2"].includes(state.phase) ? "Ra quyết định bàn giao" : "Đang chờ Freelancer"}</h2><p>Trạng thái công việc và tài chính được trình bày riêng theo từng giai đoạn.</p>${action}<p class="quiet-note">Budget 500 USD chỉ xem. Không hỗ trợ sửa ngân sách trong demo.</p></aside></div>`;
  }
  function renderClientApplications() {
    if (rank() === 0) return `${pageHead("Client / Ứng tuyển", "Đơn ứng tuyển", "<strong>00</strong> đơn trong fixture")}${empty("Chưa có ứng viên", "Freelancer chưa ứng tuyển vào công việc này.", "Quay lại công việc", "client-job")}`;
    const action = state.phase === "APPLIED" ? button("Chọn Freelancer", "assign") : `<strong>Đã chọn · ${applicationStatus()}</strong>`;
    return `${pageHead("Client / Ứng tuyển", "Chọn người làm việc", `<strong>${applicationStatus()}</strong> ${JOB.title}`)}${sectionHeading("Ứng viên cho công việc", "01 đơn fixture")}<div class="job-table"><div class="table-head"><span>#</span><span>Ứng viên / công việc</span><span>Vai trò</span><span>Ngân sách</span><span>Trạng thái đơn</span><span>Quyết định</span></div><article class="job-row featured"><span class="row-index">01</span><div class="row-main"><h3>${JOB.freelancer}</h3><p>Ứng tuyển vào ${JOB.title}</p></div><div class="cell">FREELANCER</div><div class="cell"><strong>${JOB.budget}</strong></div><div class="state-mark ${assigned() ? "mint" : ""}">${applicationStatus()}</div><div class="row-action">${action}</div></article></div><p class="quiet-note">Chọn ứng viên chuyển Job sang IN_PROGRESS và giao bước bàn giao cho Freelancer.</p>`;
  }

  function empty(title, message, buttonLabel, view) {
    return `<section class="empty-state"><h2>${title}</h2><p>${message}</p>${button(buttonLabel, "navigate", "button button-secondary", `data-view="${view}"`)}</section>`;
  }
  function workflowStep() {
    if (completed()) return 6;
    if (state.phase === "SUBMITTED_V2") return 5;
    if (state.phase === "REVISION_REQUESTED") return 4;
    if (state.phase === "SUBMITTED_V1") return 3;
    return 1;
  }
  function workflowRail() {
    const active = workflowStep();
    const tone = active === 4 ? "acid" : active === 3 || active === 5 ? "verm" : "mint";
    return `<div class="workflow-rail" aria-label="Workflow: current step ${WORK_STEPS[active]}">${WORK_STEPS.map((label, index) => `<div class="rail-cell ${index < active ? "past" : index > active ? "future" : `current ${tone}`}" ${index === active ? 'aria-current="step"' : ""}><b>0${index + 1}</b>${label}</div>`).join("")}</div>`;
  }
  function ownership() {
    if (completed()) return { title: "Workflow closed", note: "Không còn người sở hữu hành động công việc.", tone: "" };
    if (state.phase === "SUBMITTED_V1" || state.phase === "SUBMITTED_V2") return { title: "Client acts now", note: "Freelancer đợi quyết định cho bản bàn giao mới nhất.", tone: "review" };
    if (state.phase === "REVISION_REQUESTED") return { title: "Freelancer acts now", note: "Client chờ bản sửa theo feedback đã ghi nhận.", tone: "revision" };
    return { title: "Freelancer acts now", note: "Client chờ bản bàn giao đầu tiên.", tone: "" };
  }
  function workflowFrame(body, side, heading = "Workspace") {
    const owner = ownership();
    return `${state.role === "FREELANCER" ? workSubnav() : ""}${pageHead(`${state.role} / ${heading}`, JOB.title, `<strong>${jobStatus()}</strong>${JOB.budget} · chỉ xem`, `${JOB.client} · ${JOB.freelancer}`)}${workflowRail()}<div class="ownership-band ${owner.tone}"><div><strong>${owner.title}</strong><span>${owner.note}</span></div><span>JOB ${jobStatus()}</span></div><div class="workflow-grid"><article class="work-document">${body}</article><aside class="action-panel ${state.phase === "REVISION_REQUESTED" ? "acid" : completed() ? "mint" : ""}">${side}</aside></div>${submissionHistory()}`;
  }
  function submissionHistory() {
    if (!atLeast("SUBMITTED_V1")) return `<section class="history"><span class="category mint">Submission history</span><h2>00 persisted versions</h2><p class="history-empty">No submission has been sent yet.</p></section>`;
    const v1Status = state.phase === "REVISION_REQUESTED" || state.phase === "SUBMITTED_V2" || (completed() && state.approvedVersion === 2) ? "REVISION_REQUESTED" : completed() ? "APPROVED" : "SUBMITTED";
    const v2Exists = state.phase === "SUBMITTED_V2" || (completed() && state.approvedVersion === 2);
    return `<section class="history"><span class="category mint">Submission history</span><h2>0${v2Exists ? 2 : 1} persisted version${v2Exists ? "s" : ""}</h2><div class="history-row"><strong>V1</strong><strong>${v1Status}</strong><p>${esc(state.v1Summary)}${state.feedback ? ` · Feedback: ${esc(state.feedback)}` : ""}</p></div>${v2Exists ? `<div class="history-row"><strong>V2</strong><strong>${completed() ? "APPROVED" : "SUBMITTED"}</strong><p>${esc(state.v2Summary)}</p></div>` : ""}</section>`;
  }
  function submissionForm(version) {
    const prefix = version === 1 ? "v1" : "v2";
    const summary = version === 1 ? state.v1Summary : state.v2Summary;
    const url = version === 1 ? state.v1Url : state.v2Url;
    return `<form data-form="submit-${prefix}"><div class="field"><label for="${prefix}-summary">summary · required</label><textarea id="${prefix}-summary" name="${prefix}Summary" required maxlength="1500">${esc(summary)}</textarea><small>Tóm tắt bàn giao cho Client duyệt.</small></div><div class="field"><label for="${prefix}-url">deliverableUrl · optional</label><input id="${prefix}-url" name="${prefix}Url" type="url" value="${esc(url)}" placeholder="https://example.invalid/..."><small>Đường dẫn tham chiếu, không có upload hoặc attachment.</small></div><button type="submit" class="button">${version === 1 ? "Gửi bàn giao" : "Gửi bản sửa"}</button></form>`;
  }
  function renderWorkspace() {
    if (!assigned()) return `${workSubnav()}${pageHead("Freelancer / Workspace", "Công việc của tôi", "<strong>LOCKED</strong> Chưa được gán")}${empty("No active assigned job yet.", "Workspace mở khi Client chọn Freelancer cho job này.", "Xem đơn ứng tuyển", "applications")}`;
    let body = "", side = "";
    if (state.phase === "IN_PROGRESS") {
      body = `<span class="category">Current work · job brief</span><h2>Settlement infrastructure</h2><p>${JOB.description}</p><div class="work-meta">Client ${JOB.client} · Contract budget ${JOB.budget} · read only</div>`;
      side = `<div class="eyebrow">Next work action</div><h2>Prepare a first delivery</h2><p>Only summary and optional deliverableUrl enter the submission.</p>${button("Gửi bàn giao", "open-v1")}`;
    } else if (state.phase === "SUBMITTED_V1") {
      body = `<span class="category vermilion">Latest submission · V1</span><h2>Submitted for review</h2><p>${esc(state.v1Summary)}</p><div class="work-meta">deliverableUrl · ${safeLink(state.v1Url)}</div>`;
      side = `<div class="eyebrow">Client owns the next action</div><h2>Awaiting review</h2><p>Job state SUBMITTED_FOR_REVIEW. V1 is persisted; no further Freelancer action until Client decides.</p>`;
    } else if (state.phase === "REVISION_REQUESTED") {
      body = `<span class="category acid">Client feedback</span><div class="feedback-surface"><p>${esc(state.feedback)}</p></div><span class="category">Current work · prepare V2</span><h2>Bản bàn giao #2</h2><p>V1 and its feedback remain in history. Submit a new version.</p>${submissionForm(2)}`;
      side = `<div class="eyebrow">Next work action</div><h2>Respond to feedback</h2><p>Freelancer owns the revision. V2 is a new submission after confirmation.</p>`;
    } else if (state.phase === "SUBMITTED_V2") {
      body = `<span class="category vermilion">Latest submission · V2</span><h2>Resubmitted for review</h2><p>${esc(state.v2Summary)}</p><div class="work-meta">deliverableUrl · ${safeLink(state.v2Url)}</div>`;
      side = `<div class="eyebrow">Client owns the next action</div><h2>Awaiting V2 decision</h2><p>Job state SUBMITTED_FOR_REVIEW. V1 remains in history.</p>`;
    } else {
      const version = state.approvedVersion || 2;
      body = `<span class="category mint">Approved record · V${version}</span><h2>Work completed</h2><p>${esc(version === 2 ? state.v2Summary : state.v1Summary)}</p><div class="work-meta">Submission V${version} APPROVED · Job COMPLETED</div><div class="work-meta">deliverableUrl · ${safeLink(version === 2 ? state.v2Url : state.v1Url)}</div>`;
      side = `<div class="eyebrow">Workflow closed</div><h2>No work action</h2><p>Financial evidence is a separate read-only journey.</p>${button("Xem bằng chứng thanh toán", "navigate", "button", 'data-view="financial"')}`;
    }
    return workflowFrame(body, side);
  }
  function renderComposerV1() {
    if (state.phase !== "IN_PROGRESS") return renderWorkspace();
    const body = `<span class="category">Bản bàn giao #1 · draft</span><h2>Submit V1</h2><p>The summary is required; deliverableUrl is optional. No file upload.</p>${submissionForm(1)}`;
    const side = `<div class="eyebrow">Freelancer / current action</div><h2>Gửi bàn giao</h2><p>V1 enters submission history only after this local demo action.</p>`;
    return workflowFrame(body, side, "Submit V1");
  }
  function renderClientReview() {
    if (!["SUBMITTED_V1", "SUBMITTED_V2"].includes(state.phase)) {
      if (completed()) return renderCompleted();
      return `${pageHead("Client / Bàn giao", "Chờ bản bàn giao", "<strong>${jobStatus()}</strong> Client waits")}${empty("Chưa có bản cần duyệt", "Freelancer cần gửi bản bàn giao trước.", "Xem công việc", "client-job")}`;
    }
    const version = state.phase === "SUBMITTED_V2" ? 2 : 1;
    const summary = version === 2 ? state.v2Summary : state.v1Summary;
    const url = version === 2 ? state.v2Url : state.v1Url;
    const body = `<span class="category vermilion">Latest submission · V${version} / SUBMITTED</span><h2>${version === 2 ? "Revised delivery" : "First delivery"}</h2><p>${esc(summary)}</p><div class="work-meta">deliverableUrl · ${safeLink(url)}</div>${version === 2 ? `<div class="work-meta">V1 feedback retained below: ${esc(state.feedback)}</div>` : ""}`;
    const actions = version === 1
      ? `${button("Yêu cầu chỉnh sửa", "open-feedback", "button button-acid")}${button("Duyệt bàn giao", "approve", "button button-secondary")}`
      : `${button("Duyệt bàn giao", "approve")}${button("Yêu cầu chỉnh sửa", "revision-v2", "button button-secondary")}`;
    const side = `<div class="eyebrow">Client decision</div><h2>${version === 1 ? "Review V1" : "Review V2"}</h2><p>${version === 1 ? "Canonical demo route: request a revision, then review V2." : "Approve V2 to close the work and capture checkout in the demo."}</p><div class="decision-actions">${actions}</div>${version === 2 ? '<p class="quiet-note">Further versions are outside this two-version demo.</p>' : ""}`;
    return workflowFrame(body, side, `Review V${version}`);
  }
  function renderFeedback() {
    if (state.phase !== "SUBMITTED_V1") return renderClientReview();
    const body = `<span class="category acid">Client feedback · V1</span><h2>What must change?</h2><p>${esc(state.v1Summary)}</p><form data-form="feedback"><div class="field"><label for="feedback">feedback · required</label><textarea id="feedback" name="feedback" required maxlength="1500">${esc(state.feedback)}</textarea><small>Feedback attaches to V1. V2 will be a separate submission.</small></div><button type="submit" class="button button-acid">Xác nhận yêu cầu chỉnh sửa</button></form>`;
    const side = `<div class="eyebrow">Client / decision</div><h2>Request revision</h2><p>After confirmation, ownership moves to Freelancer and the Job state becomes REVISION_REQUESTED.</p>`;
    return workflowFrame(body, side, "Request Revision");
  }
  function renderCompleted() {
    if (!completed()) return renderClientJob();
    const version = state.approvedVersion || 2;
    const body = `<span class="category mint">Approved record · V${version}</span><h2>Work approved</h2><p>${esc(version === 2 ? state.v2Summary : state.v1Summary)}</p><div class="work-meta">Job COMPLETED · V${version} APPROVED · checkout CAPTURED</div><div class="work-meta">No work action owner. The payout and tax evidence have independent statuses.</div>`;
    const side = `<div class="eyebrow">Next contextual destination</div><h2>Financial evidence</h2><p>Open a read-only evidence statement. This navigation does not trigger payout.</p>${button("Xem bằng chứng thanh toán", "navigate", "button", 'data-view="financial"')}`;
    return workflowFrame(body, side, "Completed");
  }

  function financialRail() {
    return `<div class="evidence-rail" aria-label="Financial evidence lifecycle">${FINANCIAL_EVENTS.map((event, index) => {
      const number = index + 1;
      const position = number < state.financialStage ? "done" : number === state.financialStage ? "current" : "future";
      const status = number <= state.financialStage ? event.status : "CHƯA ĐẾN";
      return `<article class="evidence-row ${position}"><div class="evidence-marker">0${number}</div><div><h3>${event.title}</h3><p>${number <= state.financialStage ? event.detail : "Future demo event · no evidence claimed yet."}</p>${number <= state.financialStage ? `<details class="technical"><summary>Technical proof · DESIGN FIXTURE EVIDENCE</summary><dl><dt>Network / context</dt><dd>${number === 1 || number === 2 || number === 8 ? "Fixture record" : "DEVNET · simulated"}</dd><dt>Reference</dt><dd>${event.proof}</dd><dt>Explorer</dt><dd>Devnet reference label only · fixture IDs are not live transactions</dd></dl></details>` : ""}</div><strong class="stage-state">${status}</strong></article>`;
    }).join("")}</div>`;
  }
  function financialStatement() {
    if (state.role === "CLIENT") {
      return `<div class="statement"><div class="statement-row total"><span>Checkout captured for approved work</span><strong>500 USD</strong></div><div class="statement-row"><span>Checkout status</span><strong>CAPTURED</strong></div><div class="statement-row"><span>Job status</span><strong>COMPLETED</strong></div><div class="statement-row"><span>Settlement</span><strong>${state.financialStage >= 2 ? "Processing record exists" : "Not advanced yet"}</strong></div><div class="statement-row"><span>Tax document</span><strong>${state.financialStage >= 8 ? "DRAFT · chưa phát hành" : "Chưa có chứng từ trong demo"}</strong></div></div>`;
    }
    return `<div class="statement"><div class="statement-row total"><span>${state.financialStage >= 6 ? "VND dự kiến sau phí" : "VND dự kiến · fixture projection"}</span><strong>12,300,000 VND</strong></div><div class="statement-row"><span>Source job amount</span><strong>500 USD</strong></div><div class="statement-row"><span>Mock on-ramp net</span><strong>495.00 Mock USDC ${state.financialStage >= 3 ? "· confirmed" : "· future fixture"}</strong></div><div class="statement-row"><span>Gross VND / off-ramp fee</span><strong>12,375,000 / 75,000 VND ${state.financialStage >= 6 ? "· simulated" : "· future fixture"}</strong></div><div class="statement-row"><span>Bank destination</span><strong>TECHCOMBANK · ******6789 ${state.financialStage >= 6 ? "· recorded" : "· future fixture"}</strong></div><div class="statement-row"><span>Taxable amount</span><strong>12,600,000 VND ${state.financialStage >= 2 ? "· record" : "· future fixture"}</strong></div><div class="statement-row"><span>Certificate status</span><strong>${state.financialStage >= 8 ? "DRAFT · chưa phát hành" : "Chưa có chứng từ trong demo"}</strong></div></div>`;
  }
  function renderFinancial() {
    if (!completed()) return `${pageHead(state.role === "CLIENT" ? "Client / Thanh toán" : "Freelancer / Thu nhập", "Bằng chứng tài chính", "<strong>LOCKED</strong> Chờ work approval")}<section class="locked-financial" aria-label="Financial evidence locked"><div class="eyebrow">Financial evidence · locked</div><h2>Chờ công việc được duyệt</h2><p>Job ${esc(jobStatus())}. Bằng chứng tài chính chỉ mở sau khi Client duyệt bàn giao và checkout được ghi nhận CAPTURED.</p><div class="locked-requirement"><strong>Điều kiện mở</strong><span>Work approved → checkout CAPTURED</span></div>${button(state.role === "CLIENT" ? "Xem công việc" : "Xem workspace", "navigate", "button", `data-view="${state.role === "CLIENT" ? "client-job" : "workspace"}"`)}</section>`;
    const outcome = state.role === "CLIENT"
      ? `<strong>500 USD</strong><span>checkout CAPTURED · settlement ${state.financialStage >= 2 ? "started" : "not yet advanced"}</span>`
      : `<strong>12,300,000 VND</strong><span>VND dự kiến · ${state.financialStage >= 6 ? "simulated estimate recorded" : "fixture projection, not yet evidenced"}</span>`;
    return `<header class="financial-head"><div><div class="eyebrow">${state.role === "CLIENT" ? "Client / Thanh toán" : "Freelancer / Thu nhập"} · Job-linked statement</div><h1>Financial evidence<br>for approved work</h1><p>${JOB.title} · ${JOB.client} → ${JOB.freelancer}</p><div class="financial-flags"><span class="flag simulation">MÔ PHỎNG · SIMULATION</span><span class="flag devnet">DEVNET</span><span class="flag fixture">DESIGN FIXTURE</span></div></div><div class="financial-aside"><div class="eyebrow">${state.role === "CLIENT" ? "Checkout source" : "Estimated outcome"}</div>${outcome}</div></header><div class="disclosure">MÔ PHỎNG · DEVNET · Không có chuyển khoản ngân hàng thật. ${state.role === "FREELANCER" ? "VND dự kiến không phải tiền đã nhận hoặc số dư khả dụng." : "Checkout CAPTURED is separate from on-chain ClientPaymentStatus and bank settlement."}</div><div class="evidence-layout"><section><div class="section-heading"><h2>${state.role === "CLIENT" ? "Checkout & settlement" : "Payout statement"}</h2><small>Human summary first</small></div>${financialStatement()}<div class="section-heading"><h2>Evidence lifecycle</h2><small>0${state.financialStage} / 08 demo events</small></div>${financialRail()}</section><aside><div class="action-panel mint"><div class="eyebrow">Separate financial tracks</div><h2>What is confirmed?</h2><p>Checkout capture, payout stages, on-chain evidence, and tax document state remain separate. Advancing the demo rail never sends funds.</p><p class="quiet-note">ClientPaymentStatus.CONFIRMED is on-chain evidence, not the checkout CAPTURED status.</p>${state.financialStage >= 8 ? button("Xem chứng từ thuế", "navigate", "button", 'data-view="tax"') : `<p class="quiet-note">Tax detail unlocks at event 08 in this local fixture.</p>`}</div><details class="technical"><summary>Technical proof · DESIGN FIXTURE EVIDENCE</summary><dl><dt>Network</dt><dd>devnet</dd><dt>Rate snapshot</dt><dd>${state.financialStage >= 4 ? "FIXTURE-RATE-001" : "Not yet evidenced"}</dd><dt>Invoice</dt><dd>${state.financialStage >= 4 ? "FIXTURE-INVOICE-001" : "Not yet evidenced"}</dd><dt>Transaction signature</dt><dd>${state.financialStage >= 4 ? "FIXTURE-SIG-PAYMENT-001" : "Not yet evidenced"}</dd><dt>Withdrawal</dt><dd>${state.financialStage >= 5 ? "FIXTURE-WITHDRAWAL-001" : "Not yet evidenced"}</dd><dt>Explorer</dt><dd>DEVNET reference label; no live fixture link</dd></dl></details></aside></div>`;
  }
  function renderTax() {
    if (!completed() || state.financialStage < 8) return `${pageHead("Tax / Certificate", "Chứng từ thuế", "<strong>LOCKED</strong> Chưa đến event 08")}${empty("Tax evidence is not yet available in the demo.", "Advance the separate DEMO CONTROLS financial rail to event 08. This is not a customer-facing payout action.", "Trở lại bằng chứng tài chính", "financial")}`;
    return `${pageHead("Tax / Certificate", "Chứng từ khấu trừ", `<strong>DRAFT</strong> Đã lập chứng từ, chưa phát hành`, JOB.title)}<div class="detail-grid"><article class="tax-document"><span class="category mint">Tax record / job-linked document</span><h2>Đã lập chứng từ, chưa phát hành</h2><p class="status-line">TaxCertificateStatus.DRAFT</p><p>Job-level TaxExportStatus is SUCCESS. The certificate has its own lifecycle and is not issued or accepted in this fixture.</p><div class="statement"><div class="statement-row total"><span>Thu nhập chịu thuế</span><strong>12,600,000 VND</strong></div><div class="statement-row"><span>Source amount</span><strong>500 USD</strong></div><div class="statement-row"><span>USD/VND fixture tax rate</span><strong>25,200</strong></div><div class="statement-row"><span>Rate source</span><strong>LIVE_OPEN_ER_API</strong></div><div class="statement-row"><span>Tax withheld</span><strong>Not supplied</strong></div></div><p class="quiet-note">A separate 25,000 USDC/VND fixture rate generated the payout estimate. No statutory tax percentage is inferred.</p></article><aside class="action-panel mint"><div class="eyebrow">Document evidence</div><h2>Record exists · not issued</h2><p>Certificate identifier: <strong>FIXTURE-CERT-001</strong> (internal design fixture ID). Certificate number and lookup code: not available in this DRAFT fixture.</p><p>Created: <strong>2026-09-29 11:30</strong> (fixture timestamp).</p><p class="quiet-note">No manual issue, tax edit, or certificate creation action.</p>${button("Trở lại bằng chứng tài chính", "navigate", "button button-secondary", 'data-view="financial"')}</aside></div><details class="technical"><summary>Technical proof · DESIGN FIXTURE EVIDENCE</summary><dl><dt>Tax record ID</dt><dd>FIXTURE-TAX-001</dd><dt>Certificate internal ID</dt><dd>FIXTURE-CERT-001</dd><dt>Transaction reference</dt><dd>FIXTURE-TAX-REFERENCE-001</dd><dt>Rate source</dt><dd>LIVE_OPEN_ER_API · illustrative fixture</dd><dt>Document files</dt><dd>Not linked in this static demo</dd></dl></details>`;
  }
  function renderOverview() {
    const freelancer = state.role === "FREELANCER";
    let next;
    if (completed()) next = ["Xem bằng chứng tài chính", "Công việc đã được duyệt. Theo dõi bằng chứng mô phỏng theo từng sự kiện.", freelancer ? "Xem thu nhập" : "Xem thanh toán", "financial"];
    else if (state.phase === "SUBMITTED_V2") next = ["Client duyệt V2", freelancer ? "Bản sửa đã gửi. Chờ Client đánh giá V2." : "Bản sửa V2 đã đến lượt Client duyệt.", freelancer ? "Xem workspace" : "Duyệt V2", freelancer ? "workspace" : "review"];
    else if (state.phase === "REVISION_REQUESTED") next = ["Freelancer gửi bản sửa", freelancer ? "Phản hồi đã có. Hoàn thiện và gửi V2 trong workspace." : "Đang chờ Freelancer gửi V2.", freelancer ? "Mở workspace" : "Xem phản hồi", freelancer ? "workspace" : "client-job"];
    else if (state.phase === "SUBMITTED_V1") next = ["Client duyệt V1", freelancer ? "V1 đã gửi. Chờ Client đánh giá bàn giao." : "V1 đã đến lượt Client duyệt.", freelancer ? "Xem workspace" : "Duyệt V1", freelancer ? "workspace" : "review"];
    else if (state.phase === "IN_PROGRESS") next = [freelancer ? "Mở workspace" : "Freelancer đang làm việc", freelancer ? "Công việc đã được giao. Tiếp tục bàn giao V1." : "Freelancer đang thực hiện công việc.", freelancer ? "Mở workspace" : "Xem công việc", freelancer ? "workspace" : "client-job"];
    else if (state.phase === "APPLIED") next = ["Chờ Client chọn Freelancer", freelancer ? "Ứng tuyển đang PENDING. Client sẽ chọn Freelancer trong demo." : "Có ứng tuyển PENDING cần Client xem xét.", freelancer ? "Theo dõi ứng tuyển" : "Xem ứng tuyển", freelancer ? "applications" : "client-applications"];
    else next = ["Khám phá và ứng tuyển", freelancer ? "Mở công việc mẫu để bắt đầu hành trình." : "Công việc OPEN đang chờ ứng tuyển.", freelancer ? "Khám phá công việc" : "Xem công việc", freelancer ? "discover" : "client-jobs"];
    return `${pageHead(`${state.role} / Tổng quan`, "Công việc trước<em>.</em>", `<strong>${jobStatus()}</strong> Một hành trình công việc`, "Theo dõi công việc mẫu qua ứng tuyển, bàn giao và bằng chứng tài chính.")}<div class="overview-layout"><article class="overview-record"><div class="eyebrow">Current fixture job · 01</div><h2>${JOB.title}</h2><p>${JOB.description}</p><div class="overview-facts"><div><span>Ngân sách</span><strong>${JOB.budget}</strong></div><div><span>Client</span><strong>${JOB.client}</strong></div><div><span>Job status</span><strong>${jobStatus()}</strong></div><div><span>Application status</span><strong>${applicationStatus()}</strong></div></div><p class="quiet-note">Financial evidence opens after approved work and checkout capture.</p></article><aside class="action-panel ${state.phase === "REVISION_REQUESTED" || state.phase === "APPLIED" ? "acid" : completed() ? "mint" : ""}"><div class="eyebrow">NEXT ACTION · ${freelancer ? "FREELANCER" : "CLIENT"} VIEW</div><h2>${next[0]}</h2><p>${next[1]}</p>${button(next[2], "navigate", "button", `data-view="${next[3]}"`)}</aside></div>`;
  }
  function renderActivity() {
    const rows = [["Fixture ready", `${JOB.title} · ${JOB.budget} · ${JOB.client}`]];
    const revised = ["REVISION_REQUESTED", "SUBMITTED_V2"].includes(state.phase) || (completed() && state.approvedVersion === 2);
    if (rank() >= 1) rows.push(["Application submitted", `${JOB.freelancer} · application PENDING`]);
    if (assigned()) rows.push(["Freelancer assigned", "Application ACCEPTED · job IN_PROGRESS"]);
    if (rank() >= 3) rows.push(["Work V1 submitted", "Summary recorded · deliverable URL optional · Client review opened"]);
    if (revised && state.feedback) rows.push(["Revision requested", `Client feedback: ${state.feedback}`]);
    if (rank() >= 5 && state.approvedVersion !== 1) rows.push(["Work V2 submitted", "Revised summary recorded · deliverable URL optional · Client review opened"]);
    if (completed()) rows.push(["Work approved", `V${state.approvedVersion} approved · job COMPLETED · checkout CAPTURED`]);
    if (state.financialStage >= 2) rows.push(["Financial evidence progressed", `Event 0${state.financialStage} / 08 recorded locally · SIMULATION · DEVNET`]);
    return `${pageHead(`${state.role} / Hoạt động`, "Dòng sự kiện", `<strong>0${rows.length}</strong> Local demo events`, "Một hành trình công việc, theo thứ tự các bước đã xảy ra trong trạng thái cục bộ.")}${sectionHeading("Theo công việc", "Local state · no server event stream")}<section class="activity-list" aria-label="Local demo events">${rows.map(([label, detail], index) => `<article class="activity-row"><span class="activity-index">${String(index + 1).padStart(2, "0")}</span><div><small>Demo event · local state</small><h3>${label}</h3><p>${esc(detail)}</p></div></article>`).join("")}</section>`;
  }
  function renderAccount() {
    return `${pageHead(`${state.role} / Tài khoản`, "Tài khoản", `<strong>${state.role}</strong> Demo identity`, "Bản ghi danh tính chỉ đọc cho hành trình mẫu cục bộ.")}<section class="account-record" aria-label="Demo identity"><div class="account-row"><span>Current role</span><strong>${state.role}</strong></div><div class="account-row"><span>Demo identity</span><strong>${state.role === "CLIENT" ? JOB.client : JOB.freelancer}</strong></div><div class="account-row"><span>Connection</span><strong>Local only</strong></div><p>Role switching preserves the same fixture journey, including application, work, and financial state.</p></section>`;
  }
  function viewContent() {
    switch (state.view) {
      case "overview": return renderOverview();
      case "discover": return renderDiscover();
      case "freelancer-detail": return renderFreelancerDetail();
      case "applications": return renderApplications();
      case "my-work": return renderMyWork();
      case "workspace": return renderWorkspace();
      case "composer-v1": return renderComposerV1();
      case "client-jobs": return renderClientJobs();
      case "client-job": return renderClientJob();
      case "client-applications": return renderClientApplications();
      case "review": return renderClientReview();
      case "feedback": return renderFeedback();
      case "completed": return renderCompleted();
      case "financial": return renderFinancial();
      case "tax": return renderTax();
      case "activity": return renderActivity();
      case "account": return renderAccount();
      default: return renderOverview();
    }
  }
  function render(focus = false, top = false) {
    app.innerHTML = shell(viewContent());
    if (top) window.scrollTo(0, 0);
    if (focus) app.querySelector("#main-content").focus({ preventScroll: true });
  }
  function change(patch, message = "", top = true) {
    state = { ...state, ...patch, notice: message };
    save();
    render(true, top);
  }
  function transition(action, data = {}) {
    switch (action) {
      case "navigate": change({ view: data.view || "overview" }, ""); return;
      case "role-client": change({ role: "CLIENT", view: landingForRole("CLIENT") }, "CLIENT view · demo state preserved."); return;
      case "role-freelancer": change({ role: "FREELANCER", view: landingForRole("FREELANCER") }, "FREELANCER view · demo state preserved."); return;
      case "apply":
        if (state.phase === "DISCOVERABLE" && state.role === "FREELANCER") change({ phase: "APPLIED", view: "applications" }, "Đã ứng tuyển · application PENDING.");
        return;
      case "assign":
        if (state.phase === "APPLIED" && state.role === "CLIENT") change({ phase: "IN_PROGRESS", view: "client-job" }, "Đã chọn Freelancer · application ACCEPTED · job IN_PROGRESS.");
        return;
      case "open-v1":
        if (state.phase === "IN_PROGRESS" && state.role === "FREELANCER") change({ view: "composer-v1" });
        return;
      case "submit-v1":
        if (state.phase === "IN_PROGRESS" && state.role === "FREELANCER" && data.summary) change({ phase: "SUBMITTED_V1", view: "workspace", v1Summary: data.summary, v1Url: data.url }, "V1 SUBMITTED · job SUBMITTED_FOR_REVIEW · Client acts now.");
        return;
      case "open-feedback":
        if (state.phase === "SUBMITTED_V1" && state.role === "CLIENT") change({ view: "feedback", feedback: state.feedback || DEFAULT_FEEDBACK });
        return;
      case "submit-feedback":
        if (state.phase === "SUBMITTED_V1" && state.role === "CLIENT" && data.feedback) change({ phase: "REVISION_REQUESTED", view: "client-job", feedback: data.feedback }, "Revision requested · feedback attached to V1 · Freelancer acts now.");
        return;
      case "submit-v2":
        if (state.phase === "REVISION_REQUESTED" && state.role === "FREELANCER" && data.summary) change({ phase: "SUBMITTED_V2", view: "workspace", v2Summary: data.summary, v2Url: data.url }, "V2 SUBMITTED · job SUBMITTED_FOR_REVIEW · Client acts now.");
        return;
      case "approve":
        if (["SUBMITTED_V1", "SUBMITTED_V2"].includes(state.phase) && state.role === "CLIENT") {
          const version = state.phase === "SUBMITTED_V2" ? 2 : 1;
          change({ phase: "COMPLETED", approvedVersion: version, financialStage: 1, view: "completed" }, `V${version} APPROVED · job COMPLETED · checkout CAPTURED. Financial evidence is separate.`);
        }
        return;
      case "revision-v2": change({}, "Further revisions are outside this two-version demo. The backend supports additional review cycles.", false); return;
      case "financial-next":
        if (completed() && state.financialStage < 8) {
          const financialStage = state.financialStage + 1;
          const phase = financialStage >= 8 ? "FINANCIAL_COMPLETE" : financialStage >= 6 ? "FINANCIAL_SIMULATED" : "FINANCIAL_PROCESSING";
          change({ financialStage, phase, view: "financial" }, `DEMO CONTROL · event 0${financialStage} recorded locally; no money moved.`, false);
        }
        return;
      case "reset":
        try { localStorage.removeItem(STORAGE_KEY); storageAvailable = true; } catch (_error) { storageAvailable = false; }
        state = initialState(); render(true, true); return;
      default: return;
    }
  }
  app.addEventListener("click", event => {
    const target = event.target.closest("button[data-action]");
    if (!target || !app.contains(target) || target.disabled) return;
    transition(target.dataset.action, { view: target.dataset.view });
  });
  app.addEventListener("input", event => {
    if (["v1Summary", "v1Url", "v2Summary", "v2Url", "feedback"].includes(event.target.name)) {
      state[event.target.name] = event.target.value;
      save();
    }
  });
  app.addEventListener("submit", event => {
    const form = event.target.closest("form[data-form]");
    if (!form) return;
    event.preventDefault();
    const values = new FormData(form);
    if (form.dataset.form === "submit-v1") transition("submit-v1", { summary: String(values.get("v1Summary") || "").trim(), url: String(values.get("v1Url") || "").trim() });
    if (form.dataset.form === "submit-v2") transition("submit-v2", { summary: String(values.get("v2Summary") || "").trim(), url: String(values.get("v2Url") || "").trim() });
    if (form.dataset.form === "feedback") transition("submit-feedback", { feedback: String(values.get("feedback") || "").trim() });
  });

  render();
})();
