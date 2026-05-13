// TODO: Vitest global setup — extend matchers, mock window.matchMedia for antd.
import '@testing-library/jest-dom';

// TODO: mock window.matchMedia so antd components don't blow up in jsdom
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});
