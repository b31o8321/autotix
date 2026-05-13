// TODO: Users management page (ADMIN only).
//   - Table of users (email, name, role, enabled, last login)
//   - "Add User" modal -> createUser
//   - Per-row: change role (select), disable
//   - Frontend hides this nav item when currentUser.role !== 'ADMIN'
import { Card } from 'antd';

export default function UsersPage() {
  return <Card title="Users">{/* TODO */}</Card>;
}
