export function ButtonSymbol({ symbol }: { symbol: 'plus' | 'minus' }) {
  return <svg className="button-symbol" viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" focusable="false"
    fill="none" stroke="currentColor" strokeWidth="2" shapeRendering="crispEdges">
    <path d="M 4 8 H 12" />
    {symbol === 'plus' && <path d="M 8 4 V 12" />}
  </svg>
}
