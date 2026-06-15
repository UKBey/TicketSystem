export default function Skeleton({ className = '', style, as: Tag = 'div', ...rest }) {
  return (
    <Tag
      className={`skeleton-shimmer rounded ${className}`.trim()}
      style={style}
      {...rest}
    />
  );
}
