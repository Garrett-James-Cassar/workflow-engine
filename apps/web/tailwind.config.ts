import type { Config } from 'tailwindcss';

// Fleet UI tokens (STYLE.md v1.2 §2, §3, §5), structured like rules-engine's
// apps/web/tailwind.config.js. Colours are only ever hsl(var(--token)).
const token = (name: string) => `hsl(var(--${name}) / <alpha-value>)`;

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    // Replace (not extend) the font-size scale: only the §3 steps exist.
    fontSize: {
      xs: ['12px', { lineHeight: '16px' }],
      sm: ['13px', { lineHeight: '20px' }],
      base: ['15px', { lineHeight: '24px' }],
      lg: ['17px', { lineHeight: '24px', fontWeight: '600' }],
      empty: ['22px', { lineHeight: '28px' }],
      figure: ['28px', { lineHeight: '32px' }],
      title: ['36px', { lineHeight: '40px', letterSpacing: '-0.015em' }]
    },
    extend: {
      colors: {
        background: token('background'),
        surface: { DEFAULT: token('surface'), 2: token('surface-2') },
        border: { DEFAULT: token('border'), strong: token('border-strong') },
        ring: token('ring'),
        foreground: token('foreground'),
        muted: { foreground: token('muted-foreground') },
        primary: {
          DEFAULT: token('primary'),
          hover: token('primary-hover'),
          foreground: token('primary-foreground'),
          soft: token('primary-soft')
        },
        success: token('success'),
        warning: token('warning'),
        info: token('info'),
        paper: token('paper'),
        destructive: { DEFAULT: token('destructive'), foreground: token('destructive-foreground') }
      },
      fontFamily: {
        display: ['Fraunces', 'ui-serif', 'Georgia', 'serif'],
        sans: ['"Hanken Grotesk"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace']
      },
      borderRadius: { sm: '4px', md: '7px', lg: '10px' },
      boxShadow: {
        subtle: '0 1px 2px 0 rgb(0 0 0 / .4)',
        raised: '0 2px 8px -2px rgb(0 0 0 / .5), 0 1px 3px -1px rgb(0 0 0 / .4)'
      },
      width: { sidebar: '232px' },
      maxWidth: { content: '1280px', form: '560px', prose: '72ch', dialog: '480px' }
    }
  },
  plugins: []
} satisfies Config;
