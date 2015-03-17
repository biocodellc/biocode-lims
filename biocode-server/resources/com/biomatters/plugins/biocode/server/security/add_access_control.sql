CREATE TABLE users (
  username VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,
  firstname VARCHAR(255) NOT NULL,
  lastname VARCHAR(255) NOT NULL,
  email VARCHAR(320) NOT NULL,
  enabled BIT NOT NULL,
  is_ldap_account BIT NOT NULL,
  PRIMARY KEY(username)
);

CREATE TABLE authorities (
  username VARCHAR(255) NOT NULL,
  authority VARCHAR(50) NOT NULL,
  PRIMARY KEY (username),
  FOREIGN KEY (username) REFERENCES users (username) ON DELETE CASCADE
);

CREATE TABLE project (
  id INT NOT NULL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  external_id VARCHAR(255),
  description VARCHAR(255),
  parent_project_id INT,
  is_public BIT NOT NULL,
  FOREIGN KEY (parent) REFERENCES project(id) ON DELETE CASCADE
);

CREATE TABLE project_role (
  project_id INT NOT NULL,
  username VARCHAR(255) NOT NULL,
  role INT,
  PRIMARY KEY(project_id, username),
  FOREIGN KEY(project_id) REFERENCES project(id) ON DELETE CASCADE,
  FOREIGN KEY(username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE workflow_project (
  workflow_id INT NOT NULL PRIMARY KEY,
  project_id INT NOT NULL,
  FOREIGN KEY(project_id) REFERENCES project(id) ON DELETE CASCADE,
  FOREIGN KEY(workflow_id) REFERENCES workflow(id) ON DELETE CASCADE
);