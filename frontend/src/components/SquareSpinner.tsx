const tiles = [
  [0, 0], [4, 0], [8, 0], [12, 0],
  [12, 4], [12, 8], [12, 12],
  [8, 12], [4, 12], [0, 12],
  [0, 8], [0, 4],
]

export function SquareSpinner() {
  return <svg className="square-spinner" viewBox="0 0 15 15" shapeRendering="crispEdges" aria-hidden="true" focusable="false">
    {tiles.map(([x, y], index) => <rect key={index} x={x} y={y} width="3" height="3"
      style={{ animationDelay: `${index * 100 - 1200}ms` }} />)}
  </svg>
}
