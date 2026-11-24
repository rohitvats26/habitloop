import { Config } from '@stencil/core';

export const config: Config = {
  namespace: 'habitloop',
  outputTargets: [
    {
      type: 'dist',
      esmLoaderPath: '../loader'
    },
    {
      type: 'dist-custom-elements'
    }
  ]
};
