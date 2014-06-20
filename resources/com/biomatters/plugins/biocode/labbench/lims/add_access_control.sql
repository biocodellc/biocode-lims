-- Taken from Spring Schema
create table users (
  username varchar(255) not null primary key,
  password varchar(255) not null,
  enabled boolean not null
) ENGINE=INNODB;

-- Created for biocode server
CREATE TABLE project (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  parent INT REFERENCES project(id)
) ENGINE=INNODB;

CREATE TABLE project_roles (
  project_id INT NOT NULL REFERENCES project(id),
  username VARCHAR(255) NOT NULL REFERENCES users(username),
  role INT,
  PRIMARY KEY(project_id, username)
) ENGINE=INNODB;

CREATE TABLE plate_to_project (
  plate_id INT NOT NULL REFERENCES plate(id),
  project_id INT NOT NULL REFERENCES project(id),
  PRIMARY KEY(plate_id, project_id)
) ENGINE=INNODB;






