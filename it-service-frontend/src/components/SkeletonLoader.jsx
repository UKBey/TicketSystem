import React from 'react';

export default function SkeletonLoader({ className = 'h-6 w-full', lines = 3 }) {
  return (
    <div className={`space-y-2 ${className}`}>
      {Array.from({ length: lines }).map((_, i) => (
        <div key={`skeleton-${i}`} className="h-3 rounded bg-gray-200 dark:bg-gray-700/50 animate-pulse" />
      ))}
    </div>
  );
}
