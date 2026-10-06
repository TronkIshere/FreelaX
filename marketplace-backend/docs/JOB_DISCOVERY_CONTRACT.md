# Job discovery data contract

## Category and job skills

`category` is a stored Job enum. New creation requires an explicit category;
the server never assigns one from title keywords or profile data.

| Value | Vietnamese label |
| --- | --- |
| WEB_FRONTEND | Web / Frontend |
| BACKEND_API | Backend / API |
| SEO_CONTENT | SEO / Nội dung |
| MOBILE_APP | Ứng dụng Mobile |
| UI_UX_DESIGN | UI/UX Design |
| ECOMMERCE | Thương mại điện tử |
| DATA_ANALYTICS | Dữ liệu / Phân tích |
| BRANDING_GRAPHIC | Branding / Thiết kế đồ họa |
| OTHER | Khác |

`skills` is a Job-specific string array, independent of profile skills. Empty
is allowed. Maximum 10 entries, trimmed, each 2–40 characters, no blank/null
entries, and no case-insensitive duplicates. Case is retained for display.
Invalid category or skills returns the existing `INVALID_DATA` HTTP 400.

## Persistence and legacy rows

Marketplace uses Hibernate `ddl-auto=update` in its existing profiles; it has
no versioned migration runner. This change follows that strategy:

- `jobs.category`: non-null `varchar(30)` with SQL default `OTHER`.
- `job_skills`: ordered element collection, `job_id`, `skill_order`, `skill`
  (non-null, maximum 40 characters). Hibernate generates the collection table
  and foreign key.
- Existing rows receive `OTHER` on column creation and have no skill rows.
  No title-derived backfill and no copied profile skills.

Read response mapping occurs in a read-only transaction for lists/discovery,
participant detail and application summaries, keeping bounded skill collections
available independently of Open Session in View.

## Authoring and responses

`POST /api/v1/marketplace/jobs`: add required `category` and optional `skills`
(omitted/null means empty). All existing title, budget, deadline, deliverable,
acceptance and review constraints remain in force. No charging is introduced.

`PATCH /api/v1/marketplace/jobs/{jobId}`: optional `category` and `skills`.
Omitted/null fields preserve stored values; `skills: []` explicitly clears
skills. Existing Client ownership, OPEN eligibility and budget guards remain.
Validation of both new fields precedes persistence/mutation.

`JobResponse`, `DiscoverJobResponse` and `MyApplicationJobResponse` expose
`category` and `skills`. Legacy defaults are `OTHER` and `[]`.

The Client frontend authoring form calls these Marketplace endpoints. Creation
includes the existing required deliverables/acceptance terms; editing offers
title, description, category and skills without adding budget editing.

## Real discovery filters

`GET /api/v1/marketplace/jobs/discover` retains `page`, `size`, `keyword`,
`minBudgetUsd`, `maxBudgetUsd`, `sort`, and `application`. Keyword continues to
search title/description. Existing OPEN/unassigned eligibility, budget rules,
sort values and ALL/APPLIED/NOT_APPLIED semantics are unchanged.

Add:

- `category`: one exact enum value; omitted/empty selects all categories.
- `skills`: repeated query parameters, for example
  `?category=BACKEND_API&skills=Spring&skills=Java`.
  Raw parameter values are preserved (no implicit comma splitting).
  The same bounded normalization applies as on authoring.
  Skill matching is **ANY**, exact after trim, case-insensitive.

Category, skills, keyword, budget and application predicates compose with AND.
Skill predicates use a correlated EXISTS, so a job matching multiple selected
skills appears once and pagination totals count jobs, not skill rows. No
client-side filtering of a loaded page and no fabricated global skill taxonomy.

Frontend skill controls accept comma-separated text and send selected entries
as repeated query parameters. They do not invent suggestion lists. Rendering
uses only real job skill strings returned by Marketplace.

## Thumbnail authority and scope

The nine stored categories map directly to the approved semantic illustrations.
`OTHER` always uses Generic Development, even if title/skills contain another
family's keywords. Skills/title fallback remains only for legacy records without
a recognized persisted category. No job ID influences family selection.

No work-mode field or Remote/Hybrid/Onsite control is added. Auth, roles,
applications, assignment, contracts, submissions, finance and other services
remain outside the discovery change. The approved Overview remains frozen;
Explore is now committed and frozen at source baseline
`f42e44a5dcde0b8713fea532aa43345e943629b5`.

## Accepted runtime evidence — 2026-10-06

Marketplace's matching build/schema were used for real category/skill/combined/
exclusion/Clear validation: PASS. A local job was edited through the real Client
API for visual smoke; this is runtime evidence, not guaranteed fresh-DB seed
content. See `docs/ui/UI_VISUAL_POLISH_HANDOFF_20261006.md` for the freeze and
validation scope. A fresh runtime still needs the matching application/schema;
preserve local runtime configuration and never copy credentials into docs.
