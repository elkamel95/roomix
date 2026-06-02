/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./app/**/*.{js,jsx,ts,tsx}",
    "./src/**/*.{js,jsx,ts,tsx}",
  ],
  presets: [require("nativewind/preset")],
  theme: {
    extend: {
      colors: {
        primary: {
          50:  "#f0f4ff",
          100: "#dde8ff",
          200: "#c3d4ff",
          300: "#9db7ff",
          400: "#7090ff",
          500: "#4c6ef5",
          600: "#3a51ea",
          700: "#2f3fd6",
          800: "#2b36ad",
          900: "#293388",
        },
        dark: {
          50:  "#f5f5fa",
          100: "#eaeaf5",
          200: "#d1d1e9",
          300: "#a8a8d3",
          400: "#7a7ab8",
          500: "#5a5a9e",
          600: "#474784",
          700: "#3a3a6c",
          800: "#1e1e3f",
          900: "#0f0f23",
        },
      },
    },
  },
  plugins: [],
};
