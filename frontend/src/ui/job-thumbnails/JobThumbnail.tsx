import { CutPaperShape, RoughBurst, kineticPalette as p } from '../kinetic';
import { jobVisualIdentity, type JobFamily, type JobThumbnailSource } from './jobFamily';

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
// Approved alternatives stay inside each family's existing iconography and palette.
function VariantArtwork({ family, variant }: { family: JobFamily; variant: number }) {
  const alternate = variant === 2;
  switch (family) {
    case 'web': return <><BrowserFrame />{alternate
      ? <>{[0, 1, 2].map(i => <g key={i}><rect x={34 + i * 32} y="55" width="26" height="30" fill={i === 1 ? p.mint : p.cobalt} />
        <path d={`M${34 + i * 32} 94h23M${34 + i * 32} 103h16`} stroke={p.ink} strokeWidth="3" /></g>)}</>
      : <><rect x="34" y="55" width="44" height="48" fill={p.cobalt} /><path d="M35 103L52 74L77 103Z" fill={p.mint} />
        <path d="M88 59H128M88 72H121M88 85H125" stroke={p.ink} strokeWidth="4" /><rect x="88" y="97" width="29" height="10" fill={p.acid} /></>}
      <Cursor /></>;
    case 'backend': return <><rect x="24" y="26" width="66" height="53" fill={p.ink} />
      <path d="M47 42L36 53L47 64M66 42L77 53L66 64" stroke={p.cream} strokeWidth="4" fill="none" />
      {alternate ? <><path d="M63 80V104H103" stroke={p.vermilion} strokeWidth="5" fill="none" />
        <path d="M89 59V112C89 128 143 128 143 112V59" fill={p.cobalt} stroke={p.ink} strokeWidth="3" />
        {[59, 79, 99].map(y => <ellipse key={y} cx="116" cy={y} rx="27" ry="9" fill={p.cobalt} stroke={p.ink} strokeWidth="3" />)}</>
      : <>{[0, 1, 2].map(i => <g key={i}><rect x="53" y={76 + i * 20} width="87" height="19" fill={p.cobalt} stroke={p.ink} strokeWidth="3" />
        <circle cx="66" cy={85 + i * 20} r="3" fill={p.mint} /><path d={`M83 ${85 + i * 20}H129`} stroke={p.cream} strokeWidth="4" /></g>)}</>}</>;
    case 'seo': return <><BrowserFrame />{alternate ? <><path d="M38 110V87H53V110M61 110V74H76V110M84 110V59H99V110" fill={p.cobalt} />
        <path d="M37 82L65 64L86 70L123 53M111 53H123V66" stroke={p.vermilion} strokeWidth="5" fill="none" />
        <circle cx="122" cy="99" r="14" fill={p.mint} stroke={p.ink} strokeWidth="4" /><path d="M132 110L145 123" stroke={p.ink} strokeWidth="6" /></>
      : <><rect x="34" y="53" width="93" height="14" fill={p.mint} />
        <path d="M35 80H87M35 87H72M35 99H81M35 106H66" stroke={p.ink} strokeWidth="3" />
        <circle cx="111" cy="88" r="20" fill={p.cream} stroke={p.ink} strokeWidth="6" /><path d="M124 105L144 127" stroke={p.ink} strokeWidth="8" />
        <circle cx="111" cy="88" r="12" fill={p.cobalt} /></>}</>;
    case 'mobile': return <g transform="rotate(4 80 80)"><rect x="43" y="16" width="80" height="119" rx="12" fill={p.ink} />
      <rect x="49" y="23" width="68" height="104" rx="7" fill={p.cream} /><path d="M65 24H100" stroke={p.ink} strokeWidth="7" />
      {alternate ? <>{[0, 1, 2, 3].map(i => <rect key={i} x={59 + i % 2 * 26} y={43 + Math.floor(i / 2) * 31} width="20" height="24" fill={i % 2 ? p.mint : p.cobalt} />)}
        <path d="M60 111H105" stroke={p.vermilion} strokeWidth="5" /></>
      : <><rect x="58" y="43" width="49" height="33" fill={p.cobalt} /><path d="M60 91H103M60 102H90" stroke={p.ink} strokeWidth="4" />
        <rect x="60" y="112" width="43" height="8" fill={p.acid} /></>}</g>;
    case 'uiux': return <><BrowserFrame /><path d="M34 54H127M34 110H127" stroke={p.ink} strokeWidth="2" />
      {alternate ? <>{[0, 1, 2].map(i => <g key={i}><rect x={35 + i * 31} y="66" width="24" height="31" fill={p.mint} stroke={p.ink} strokeWidth="2" />
        <path d={`M${35 + i * 31} 66l24 31M${59 + i * 31} 66l-24 31`} stroke={p.ink} strokeWidth="2" /></g>)}</>
      : <><rect x="34" y="64" width="61" height="36" fill={p.mint} stroke={p.ink} strokeWidth="2" /><path d="M34 64L95 100M95 64L34 100" stroke={p.ink} strokeWidth="2" />
        <path d="M105 65H128M105 79H128M105 93H122" stroke={p.cobalt} strokeWidth="5" /></>}<Cursor /></>;
    case 'ecommerce': return <><rect x="26" y="69" width="106" height="60" fill={p.cream} stroke={p.ink} strokeWidth="4" />
      <path d="M22 72L35 53H124L138 72Z" fill={p.vermilion} stroke={p.ink} strokeWidth="3" />
      <path d="M48 55V71M70 55V71M92 55V71M113 55V71" stroke={p.cream} strokeWidth="9" />
      <rect x="39" y="91" width="27" height="37" fill={p.cobalt} />
      {alternate ? <><rect x="75" y="83" width="46" height="38" fill={p.acid} /><path d="M82 92H88L92 106H110L115 95H90" stroke={p.ink} strokeWidth="3" fill="none" />
        <circle cx="95" cy="113" r="3" fill={p.ink} /><circle cx="108" cy="113" r="3" fill={p.ink} /></>
      : <><rect x="77" y="88" width="43" height="26" fill={p.mint} /><path d="M53 49L50 22H103L101 49Z" fill={p.acid} stroke={p.ink} strokeWidth="3" />
        <path d="M64 23C64 5 91 5 91 23" stroke={p.ink} strokeWidth="4" fill="none" /></>}</>;
    case 'data': return <><BrowserFrame />{alternate ? <><circle cx="66" cy="79" r="24" fill={p.cobalt} />
        <path d="M66 55V79H90" stroke={p.acid} strokeWidth="12" fill="none" /><path d="M105 59H128M105 76H121M105 93H127" stroke={p.ink} strokeWidth="4" />
        <path d="M36 112L59 105L74 112L93 101L126 108" stroke={p.ink} strokeWidth="3" fill="none" /></>
      : <><rect x="34" y="53" width="94" height="47" fill={p.ink} /><path d="M44 91V78M62 91V68M80 91V74M98 91V59M116 91V65" stroke={p.mint} strokeWidth="8" />
        <path d="M36 111H60M76 111H97M113 111H128" stroke={p.cobalt} strokeWidth="4" /></>}</>;
    case 'branding': return <><rect x="24" y="27" width="91" height="78" fill={p.ink} transform="rotate(-5 70 65)" />
      {alternate ? <><circle cx="68" cy="65" r="25" fill="none" stroke={p.cream} strokeWidth="10" /><path d="M69 55L92 89" stroke={p.vermilion} strokeWidth="8" /></>
      : <><path d="M43 92V42H76C106 42 103 70 78 70H43M78 70L101 93" stroke={p.cream} strokeWidth="8" fill="none" /></>}
      <rect x="112" y="49" width="34" height="60" fill={p.cream} stroke={p.ink} strokeWidth="2" /><text x="116" y="78" fontSize="22" fontWeight="800" fill={p.ink}>Aa</text>
      {[p.vermilion, p.mint, p.cobalt].map((color, i) => <rect key={color} x={32 + i * 30} y="116" width="25" height="15" fill={color} stroke={p.ink} strokeWidth="2" />)}</>;
    default: return <><BrowserFrame /><rect x="33" y="53" width="95" height="57" fill={p.ink} />
      {alternate ? <><path d="M50 68L39 80L50 92M99 68L110 80L99 92M84 63L69 99" stroke={p.cream} strokeWidth="5" fill="none" /></>
      : <><path d="M44 67L57 79L44 91M66 94H99" stroke={p.mint} strokeWidth="5" fill="none" /><path d="M103 66H119M103 77H119" stroke={p.cobalt} strokeWidth="3" /></>}</>;
  }
}

function Artwork({ family, variant }: { family: JobFamily; variant: number }) {
  if (variant !== 0) return <VariantArtwork family={family} variant={variant} />;
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
  const { family, variant, visualKey, roughKey } = jobVisualIdentity(job);
  const accent = family === 'backend' || family === 'seo' ? 'mint' : family === 'ecommerce' || family === 'branding' ? 'vermilion' : 'cobalt';
  return <span className="job-family-art" data-family={family} data-visual-key={visualKey} data-rough-key={roughKey} aria-hidden="true">
    <CutPaperShape accent={accent} rotation={-6} texture className="job-family-paper" />
    <CutPaperShape accent="acid" rotation={6} className="job-family-paper job-family-paper-back" />
    <CutPaperShape accent={accent} variant="strip" rotation={-3} className="job-family-tape" />
    <svg viewBox="0 0 164 146" className="job-family-drawing" focusable="false"><Artwork family={family} variant={variant} /></svg>
    <RoughBurst seedKey={roughKey} accent="ink" size={30} className="job-family-burst" />
  </span>;
}
