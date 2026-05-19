export default function Skeleton({ className = '', style, as: Tag = 'div', ...rest }) {
  return (
    <Tag
      className={`animate-pulse rounded ${className}`.trim()}
      style={{ backgroundColor: 'var(--bg-surface-secondary)', ...style }}
      {...rest}
    />
  );
}
