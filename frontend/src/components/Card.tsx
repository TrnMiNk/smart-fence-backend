// Thẻ chứa nội dung giao diện có viền và đổ bóng nhẹ
import React from 'react';

interface CardProps {
  children: React.ReactNode;
  className?: string;
}

export const Card: React.FC<CardProps> = ({ children, className = '' }) => {
  return (
    <div
      className={`bg-[#101a29] border border-white/[0.07] rounded-xl shadow-[0_10px_30px_rgba(0,0,0,.12)] p-4 text-slate-100 ${className}`}
    >
      {children}
    </div>
  );
};
