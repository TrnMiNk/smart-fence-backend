/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        navy: {
          950: '#070d18',
          900: '#0b1329',
          850: '#0f1a36',
          800: '#142247',
          700: '#1d2f5e',
          600: '#2b4484',
        },
        status: {
          safe: '#22c55e',      // xanh lá an toàn
          warning: '#eab308',   // vàng nghi ngờ
          danger: '#ef4444',    // đỏ xâm nhập
          offline: '#64748b',   // xám ngoại tuyến
        }
      },
    },
  },
  plugins: [],
}
