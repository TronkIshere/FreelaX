import { CutPaperShape, RoughBurst, kineticPalette as p } from '../kinetic';
import { jobFamily, type JobFamily, type JobThumbnailSource } from './jobFamily';

function BrowserFrame() {
  return <><rect x="23" y="28" width="116" height="93" fill={p.cream} stroke={p.ink} strokeWidth="5" />
    <path d="M25 44H137" stroke={p.ink} strokeWidth="3" />
    <circle cx="33" cy="36" r="3" fill={p.vermilion} /><circle cx="43" cy="36" r="3" fill={p.acid} />
    <circle cx="53" cy="36" r="3" fill={p.mint} /></>;
}
function Cursor() {
  return <path d="M112 92L114 132L124 121L133 136L142 130L131 115L146 112Z"
    fill={p.ink} stroke={p.cream} strokeWidth="3" strokeLinejoin="miter" />;
}
function Artwork({ family }: { family: JobFamily }) {
  switch (family) {
    case 'web': return <><BrowserFrame /><rect x="34" y="54" width="94" height="35" fill={p.cobalt} />
      <path d="M35 89L58 63L74 80L92 67L127 89Z" fill={p.ink} /><circle cx="108" cy="65" r="7" fill={p.vermilion} />
      <rect x="34" y="99" width="35" height="10" fill={p.acid} /><path d="M79 99H126M79 109H111" stroke={p.ink} strokeWidth="4" /><Cursor /></>;
    case 'backend': return <><rect x="21" y="24" width="101" height="79" fill={p.ink} />
      <path d="M48 43L37 53L48 63M80 43L91 53L80 63M69 40L60 68" stroke={p.cream} strokeWidth="5" fill="none" />
      <path d="M31 80H60L52 72M31 91H53" stroke={p.vermilion} strokeWidth="4" fill="none" />
      {[0, 1, 2].map(i => <g key={i}><rect x="75" y={77 + i * 18} width="68" height="17" fill={p.cobalt} stroke={p.ink} strokeWidth="3" />
        <circle cx="85" cy={85 + i * 18} r="2" fill={p.cream} /><path d={`M101 ${85 + i * 18}H132`} stroke={p.cream} strokeWidth="3" /></g>)}</>;
    case 'seo': return <><BrowserFrame /><rect x="36" y="54" width="91" height="12" fill={p.mint} />
      <path d="M47 109V94H59V109M68 109V83H80V109M89 109V74H101V109" fill={p.cobalt} />
      <circle cx="50" cy="70" r="23" fill={p.cream} stroke={p.ink} strokeWidth="7" /><circle cx="50" cy="70" r="17" fill={p.mint} />
      <path d="M34 87L15 112" stroke={p.ink} strokeWidth="10" /><path d="M108 113L139 76L125 80M139 76L141 92" fill="none" stroke={p.vermilion} strokeWidth="7" /></>;
    case 'mobile': return <g transform="rotate(-6 80 80)"><rect x="44" y="16" width="79" height="119" rx="12" fill={p.ink} />
      <rect x="50" y="23" width="67" height="103" rx="7" fill={p.cream} /><path d="M65 24H102" stroke={p.ink} strokeWidth="7" />
      <rect x="59" y="40" width="25" height="25" fill={p.cobalt} /><rect x="89" y="40" width="18" height="25" fill={p.acid} />
      <rect x="59" y="74" width="48" height="31" fill={p.mint} /><path d="M59 105L78 82L106 105Z" fill={p.ink} />
      <path d="M64 116H101" stroke={p.vermilion} strokeWidth="5" /></g>;
    case 'uiux': return <><BrowserFrame /><rect x="34" y="56" width="43" height="46" fill={p.mint} stroke={p.ink} strokeWidth="2" />
      <path d="M35 57L76 101M76 57L35 101M86 57H127M86 67H113M86 77H123" stroke={p.ink} strokeWidth="2" />
      <rect x="87" y="90" width="37" height="12" fill={p.cobalt} />
      <circle cx="40" cy="113" r="4" fill={p.vermilion} /><circle cx="54" cy="113" r="4" fill={p.acid} /><Cursor /></>;
    case 'ecommerce': return <><rect x="28" y="73" width="101" height="57" fill={p.cream} stroke={p.ink} strokeWidth="4" />
      <path d="M22 77L33 59H125L138 77Z" fill={p.vermilion} stroke={p.ink} strokeWidth="3" />
      <path d="M46 61V77M67 61V77M89 61V77M113 61V77" stroke={p.cream} strokeWidth="10" />
      <rect x="42" y="93" width="28" height="37" fill={p.cobalt} /><rect x="80" y="92" width="33" height="23" fill={p.mint} />
      <path d="M58 58L55 29H105L101 58Z" fill={p.acid} stroke={p.ink} strokeWidth="3" />
      <path d="M67 31V23C67 6 94 6 94 23V31" stroke={p.ink} strokeWidth="4" fill="none" /></>;
    case 'data': return <><BrowserFrame /><rect x="33" y="53" width="52" height="47" fill={p.ink} />
      <path d="M41 91V80M54 91V66M67 91V72M80 91V59" stroke={p.mint} strokeWidth="7" />
      <circle cx="110" cy="76" r="18" fill={p.cobalt} /><path d="M110 58V76H128" stroke={p.acid} strokeWidth="7" fill="none" />
      <path d="M36 111H69M83 112L94 105L106 115L119 104L132 109" stroke={p.ink} strokeWidth="3" fill="none" /></>;
    case 'branding': return <><rect x="24" y="25" width="89" height="80" fill={p.ink} transform="rotate(-5 70 65)" />
      <path d="M41 91L72 39L104 91H87L72 65L57 91Z" fill={p.cream} /><path d="M62 91L72 75L82 91Z" fill={p.vermilion} />
      <rect x="109" y="49" width="36" height="61" fill={p.cream} stroke={p.ink} strokeWidth="2" />
      <text x="114" y="77" fontSize="23" fontWeight="800" fill={p.ink}>Aa</text>
      {[p.vermilion, p.mint, p.cobalt].map((color, i) => <rect key={color} x={32 + i * 30} y="116" width="25" height="15" fill={color} stroke={p.ink} strokeWidth="2" />)}</>;
    default: return <><BrowserFrame /><path d="M58 66L43 81L58 96M103 66L118 81L103 96M90 60L72 104"
      stroke={p.ink} strokeWidth="6" fill="none" /><path d="M35 113H71" stroke={p.cobalt} strokeWidth="5" /></>;
  }
}

export function JobThumbnail({ job }: { job: JobThumbnailSource }) {
  const family = jobFamily(job);
  const accent = family === 'backend' || family === 'seo' ? 'mint' : family === 'ecommerce' || family === 'branding' ? 'vermilion' : 'cobalt';
  return <span className="job-family-art" data-family={family} aria-hidden="true">
    <CutPaperShape accent={accent} rotation={-6} texture className="job-family-paper" />
    <CutPaperShape accent="acid" rotation={6} className="job-family-paper job-family-paper-back" />
    <CutPaperShape accent={accent} variant="strip" rotation={-3} className="job-family-tape" />
    <svg viewBox="0 0 164 146" className="job-family-drawing" focusable="false"><Artwork family={family} /></svg>
    <RoughBurst seedKey={'job-family:' + family} accent="ink" size={30} className="job-family-burst" />
  </span>;
}
