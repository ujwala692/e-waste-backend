CREATE DATABASE IF NOT EXISTS recircle CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE recircle;

-- Hibernate manages these tables from the matching JPA entities. This file documents
-- the intended MySQL schema for review or manual database setup.
CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  phone VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,
  address VARCHAR(255) NOT NULL,
  city VARCHAR(255) NOT NULL,
  state VARCHAR(255) NOT NULL,
  pincode VARCHAR(255) NOT NULL,
  role VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ewaste (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  category VARCHAR(255) NOT NULL,
  item_name VARCHAR(255) NOT NULL,
  brand VARCHAR(255),
  quantity INT NOT NULL,
  weight DOUBLE NOT NULL,
  `condition` VARCHAR(255) NOT NULL,
  description VARCHAR(1200),
  pickup_address VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ewaste_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS collection_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_number VARCHAR(255) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  ewaste_id BIGINT NOT NULL,
  pickup_address VARCHAR(255) NOT NULL,
  pickup_date DATE NOT NULL,
  preferred_time VARCHAR(255) NOT NULL,
  notes VARCHAR(1000),
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_collection_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_collection_ewaste FOREIGN KEY (ewaste_id) REFERENCES ewaste(id)
);
