-- --------------------------------------------------------
-- Host:                         127.0.0.1
-- Server version:               8.0.23 - MySQL Community Server - GPL
-- Server OS:                    Win64
-- HeidiSQL Version:             12.0.0.6468
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

-- Dumping structure for table luna_players.skills_data
CREATE TABLE IF NOT EXISTS `skills_data` (
  `id` int NOT NULL,
  `attack_xp` double NOT NULL,
  `attack_level` tinyint unsigned NOT NULL,
  `defence_xp` double NOT NULL,
  `defence_level` tinyint unsigned NOT NULL,
  `strength_xp` double NOT NULL,
  `strength_level` tinyint unsigned NOT NULL,
  `hitpoints_xp` double NOT NULL,
  `hitpoints_level` tinyint unsigned NOT NULL,
  `ranged_xp` double NOT NULL,
  `ranged_level` tinyint unsigned NOT NULL,
  `prayer_xp` double NOT NULL,
  `prayer_level` tinyint unsigned NOT NULL,
  `magic_xp` double NOT NULL,
  `magic_level` tinyint unsigned NOT NULL,
  `cooking_xp` double NOT NULL,
  `cooking_level` tinyint unsigned NOT NULL,
  `woodcutting_xp` double NOT NULL,
  `woodcutting_level` tinyint unsigned NOT NULL,
  `fletching_xp` double NOT NULL,
  `fletching_level` tinyint unsigned NOT NULL,
  `fishing_xp` double NOT NULL,
  `fishing_level` tinyint unsigned NOT NULL,
  `firemaking_xp` double NOT NULL,
  `firemaking_level` tinyint unsigned NOT NULL,
  `crafting_xp` double NOT NULL,
  `crafting_level` tinyint unsigned NOT NULL,
  `smithing_xp` double NOT NULL,
  `smithing_level` tinyint unsigned NOT NULL,
  `mining_xp` double NOT NULL,
  `mining_level` tinyint unsigned NOT NULL,
  `herblore_xp` double NOT NULL,
  `herblore_level` tinyint unsigned NOT NULL,
  `agility_xp` double NOT NULL,
  `agility_level` tinyint unsigned NOT NULL,
  `thieving_xp` double NOT NULL,
  `thieving_level` tinyint unsigned NOT NULL,
  `slayer_xp` double NOT NULL,
  `slayer_level` tinyint unsigned NOT NULL,
  `farming_xp` double NOT NULL,
  `farming_level` tinyint unsigned NOT NULL,
  `runecrafting_xp` double NOT NULL,
  `runecrafting_level` tinyint unsigned NOT NULL,
  `total_level` smallint unsigned NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Data exporting was unselected.

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
