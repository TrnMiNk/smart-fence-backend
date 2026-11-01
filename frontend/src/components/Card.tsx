// Thẻ chứa nội dung giao diện có viền và đổ bóng nhẹ
import React from 'react';

interface CardProps {
  children: React.ReactNode;
  className?: string;
}

export const Card: React.FC<CardProps> = ({ children, className = '' }) => {
  return (
    <div
      className={`bg-navy-900 border border-navy-800 rounded-lg shadow p-4 text-slate-100 ${className}`}
    >
      {children}
    </div>
  );
};
