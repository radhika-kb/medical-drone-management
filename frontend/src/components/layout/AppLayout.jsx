import React from 'react';
import Header from './Header';
import Sidebar from './Sidebar';

export const AppLayout = ({ children, pageTitle = 'OVERVIEW', breadcrumb = 'Central Operations • Bengaluru • All Zones' }) => {
  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--color-background)' }}>
      <Sidebar />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <Header pageTitle={pageTitle} breadcrumb={breadcrumb} />
        <main style={{
          flex: 1,
          padding: '24px 32px',
          maxWidth: '1600px',
          width: '100%',
          margin: '0 auto',
        }}>
          {children}
        </main>
      </div>
    </div>
  );
};

export default AppLayout;
