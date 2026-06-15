import React from 'react';

export default function SkeletonLoader({ className = 'h-6 w-full', lines = 3 }) {
  return (
    <div className={`space-y-2 ${className}`} aria-hidden="true">
      {Array.from({ length: lines }).map((_, i) => (
        <div key={`skeleton-${i}`} className="h-3 rounded skeleton-shimmer" />
      ))}
    </div>
  );
}
