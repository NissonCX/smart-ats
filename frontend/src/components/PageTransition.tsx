import { motion } from 'framer-motion';
import type { ReactNode } from 'react';

interface Props {
  children: ReactNode;
  className?: string;
}

// Check for reduced motion preference
const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const pageVariants = {
  initial: {
    opacity: 0,
    y: prefersReducedMotion() ? 0 : 12,
  },
  animate: {
    opacity: 1,
    y: 0,
  },
  exit: {
    opacity: 0,
    y: prefersReducedMotion() ? 0 : -8,
  },
};

const pageTransition = prefersReducedMotion()
  ? { duration: 0 }
  : {
      type: 'spring' as const,
      stiffness: 260,
      damping: 30,
      duration: 0.3,
    };

export default function PageTransition({ children, className }: Props) {
  return (
    <motion.div
      variants={pageVariants}
      initial="initial"
      animate="animate"
      exit="exit"
      transition={pageTransition}
      className={className}
    >
      {children}
    </motion.div>
  );
}
