-- ╔══════════════════════════════════════════════════════════════╗
-- ║         MySQL INIT SCRIPT                                    ║
-- ║                                                              ║
-- ║  Runs once when MySQL container first starts.                ║
-- ║  Creates a separate database for each microservice.          ║
-- ║                                                              ║
-- ║  Why separate databases per service?                         ║
-- ║    • True data isolation — services can't accidentally        ║
-- ║      join across service boundaries                          ║
-- ║    • Independent schema evolution                            ║
-- ║    • In production: each gets its own RDS instance          ║
-- ╚══════════════════════════════════════════════════════════════╝

-- Create all databases
CREATE DATABASE IF NOT EXISTS shopnest_users     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS shopnest_products  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS shopnest_orders    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS shopnest_payments  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS shopnest_inventory CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS shopnest_notifications CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS shopnest_shipping  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS shopnest_reviews   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant 'shopnest' user access to all databases
-- This user is what each microservice connects with
GRANT ALL PRIVILEGES ON shopnest_users.*         TO 'shopnest'@'%';
GRANT ALL PRIVILEGES ON shopnest_products.*      TO 'shopnest'@'%';
GRANT ALL PRIVILEGES ON shopnest_orders.*        TO 'shopnest'@'%';
GRANT ALL PRIVILEGES ON shopnest_payments.*      TO 'shopnest'@'%';
GRANT ALL PRIVILEGES ON shopnest_inventory.*     TO 'shopnest'@'%';
GRANT ALL PRIVILEGES ON shopnest_notifications.* TO 'shopnest'@'%';
GRANT ALL PRIVILEGES ON shopnest_shipping.*      TO 'shopnest'@'%';
GRANT ALL PRIVILEGES ON shopnest_reviews.*       TO 'shopnest'@'%';

FLUSH PRIVILEGES;
