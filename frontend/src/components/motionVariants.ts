/** Shared Framer Motion animation variants with reduced-motion support */

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const baseTransition = prefersReducedMotion()
  ? { duration: 0 }
  : {
      type: 'spring' as const,
      stiffness: 260,
      damping: 30,
    };

export const staggerContainer = {
  animate: {
    transition: prefersReducedMotion()
      ? {}
      : {
          staggerChildren: 0.06,
        },
  },
};

export const staggerItem = {
  initial: { opacity: 0, y: prefersReducedMotion() ? 0 : 12 },
  animate: {
    opacity: 1,
    y: 0,
    transition: baseTransition,
  },
};

// Fade variants
export const fadeInUp = {
  initial: { opacity: 0, y: prefersReducedMotion() ? 0 : 12 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: prefersReducedMotion() ? 0 : -8 },
};

export const fadeIn = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
};

// Scale variants
export const scaleIn = {
  initial: { opacity: 0, scale: prefersReducedMotion() ? 1 : 0.95 },
  animate: { opacity: 1, scale: 1 },
  exit: { opacity: 0, scale: prefersReducedMotion() ? 1 : 0.95 },
};
