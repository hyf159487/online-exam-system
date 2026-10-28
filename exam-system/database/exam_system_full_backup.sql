-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: exam_system
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `exam_system`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `exam_system` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `exam_system`;

--
-- Table structure for table `exam`
--

DROP TABLE IF EXISTS `exam`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '考试ID',
  `exam_name` varchar(100) NOT NULL COMMENT '考试名称',
  `paper_id` bigint NOT NULL COMMENT '关联试卷 exam_paper.id',
  `course_id` bigint DEFAULT NULL COMMENT '学科（冗余，方便筛选）',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `duration` int NOT NULL DEFAULT '60' COMMENT '考试时长（分钟）',
  `total_score` decimal(6,1) DEFAULT '0.0' COMMENT '总分（冗余自试卷）',
  `pass_score` decimal(6,1) DEFAULT '60.0' COMMENT '及格分（冗余自试卷）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1=已发布 0=已撤回',
  `creator_id` bigint DEFAULT NULL COMMENT '发布人（sys_user.id）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_paper` (`paper_id`),
  KEY `idx_time` (`start_time`,`end_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试发布表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam`
--

LOCK TABLES `exam` WRITE;
/*!40000 ALTER TABLE `exam` DISABLE KEYS */;
INSERT INTO `exam` VALUES (2,'错题本端到端测试考试',3,1,'2026-08-19 13:12:23','2026-08-20 14:12:23',120,9.0,6.0,1,8,'2026-08-19 14:12:23','2026-08-19 14:12:23');
/*!40000 ALTER TABLE `exam` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exam_answer`
--

DROP TABLE IF EXISTS `exam_answer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_answer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '答题记录ID',
  `record_id` bigint NOT NULL COMMENT '考试记录ID',
  `question_id` bigint NOT NULL COMMENT '试题ID',
  `user_answer` text COMMENT '学生答案',
  `is_correct` tinyint DEFAULT NULL COMMENT '是否正确：0错误 1正确（客观题自动判，主观题教师判）',
  `get_score` decimal(5,1) DEFAULT NULL COMMENT '得分',
  `teacher_comment` text COMMENT '教师评语（主观题）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答题记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam_answer`
--

LOCK TABLES `exam_answer` WRITE;
/*!40000 ALTER TABLE `exam_answer` DISABLE KEYS */;
INSERT INTO `exam_answer` VALUES (13,16,1,'A',0,0.0,NULL,'2026-08-19 14:12:31'),(14,16,3,'A,C',0,0.0,NULL,'2026-08-19 14:12:31'),(15,16,5,'T',1,2.0,NULL,'2026-08-19 14:12:31'),(16,16,7,'wrong',0,0.0,NULL,'2026-08-19 14:12:31');
/*!40000 ALTER TABLE `exam_answer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exam_paper`
--

DROP TABLE IF EXISTS `exam_paper`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_paper` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '试卷ID',
  `paper_name` varchar(100) NOT NULL COMMENT '试卷名称',
  `course_id` bigint NOT NULL COMMENT '所属课程ID',
  `paper_type` tinyint NOT NULL DEFAULT '1' COMMENT '组卷方式：1手动组卷 2随机组卷',
  `total_score` decimal(6,1) NOT NULL COMMENT '试卷总分',
  `duration` int NOT NULL DEFAULT '60' COMMENT '考试时长（分钟）',
  `pass_score` decimal(6,1) NOT NULL COMMENT '及格分数',
  `start_time` datetime DEFAULT NULL COMMENT '考试开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '考试结束时间',
  `creator_id` bigint NOT NULL COMMENT '创建人ID（教师）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0草稿 1已发布 2已结束',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam_paper`
--

LOCK TABLES `exam_paper` WRITE;
/*!40000 ALTER TABLE `exam_paper` DISABLE KEYS */;
INSERT INTO `exam_paper` VALUES (1,'Java 期末模拟卷一',1,1,100.0,120,60.0,NULL,NULL,1,1,'2026-08-18 19:02:33','2026-08-18 19:02:33'),(2,'Java 期中测试',1,1,60.0,90,60.0,NULL,NULL,1,0,'2026-08-18 19:02:33','2026-08-18 20:42:22'),(3,'Java 期末模拟卷一',1,1,100.0,120,60.0,NULL,NULL,1,1,'2026-08-18 19:02:52','2026-08-18 19:02:52'),(4,'Java 期中测试',1,2,60.0,90,60.0,NULL,NULL,1,1,'2026-08-18 19:02:52','2026-08-18 21:54:35'),(5,'测试试卷',2,1,0.0,60,60.0,NULL,NULL,1,0,'2026-08-22 02:51:11','2026-08-22 02:51:11');
/*!40000 ALTER TABLE `exam_paper` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exam_record`
--

DROP TABLE IF EXISTS `exam_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '考试记录ID',
  `paper_id` bigint NOT NULL COMMENT '试卷ID',
  `user_id` bigint NOT NULL COMMENT '学生ID',
  `start_time` datetime DEFAULT NULL COMMENT '开始答题时间',
  `submit_time` datetime DEFAULT NULL COMMENT '交卷时间',
  `total_score` decimal(6,1) DEFAULT NULL COMMENT '总得分',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0未完成 1已交卷 2已批改',
  `is_pass` tinyint DEFAULT NULL COMMENT '是否及格：0不及格 1及格',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_paper_user` (`paper_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam_record`
--

LOCK TABLES `exam_record` WRITE;
/*!40000 ALTER TABLE `exam_record` DISABLE KEYS */;
INSERT INTO `exam_record` VALUES (1,1,2,'2026-08-17 09:00:00','2026-08-17 09:52:00',92.0,2,1,'2026-08-18 22:34:29'),(2,1,3,'2026-08-17 09:00:00','2026-08-17 09:55:00',85.0,2,1,'2026-08-18 22:34:29'),(3,1,4,'2026-08-17 09:00:00','2026-08-17 09:58:00',78.0,2,1,'2026-08-18 22:34:29'),(4,1,5,'2026-08-17 09:00:00','2026-08-17 09:59:00',65.0,2,1,'2026-08-18 22:34:29'),(5,1,6,'2026-08-17 09:00:00','2026-08-17 09:47:00',55.0,2,0,'2026-08-18 22:34:29'),(6,1,7,'2026-08-17 09:00:00','2026-08-17 09:50:00',88.0,2,1,'2026-08-18 22:34:29'),(7,2,2,'2026-08-15 14:00:00','2026-08-15 14:40:00',54.0,2,1,'2026-08-18 22:34:29'),(8,2,3,'2026-08-15 14:00:00','2026-08-15 14:45:00',48.0,2,1,'2026-08-18 22:34:29'),(9,2,4,'2026-08-15 14:00:00','2026-08-15 14:52:00',42.0,2,1,'2026-08-18 22:34:29'),(10,2,5,'2026-08-15 14:00:00','2026-08-15 14:55:00',38.0,2,1,'2026-08-18 22:34:29'),(11,2,6,'2026-08-15 14:00:00','2026-08-15 14:38:00',30.0,2,0,'2026-08-18 22:34:29'),(12,2,7,'2026-08-15 14:00:00','2026-08-15 14:42:00',57.0,2,1,'2026-08-18 22:34:29'),(16,3,2,'2026-08-19 14:12:31','2026-08-19 14:12:31',2.0,2,0,'2026-08-19 14:12:31');
/*!40000 ALTER TABLE `exam_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `knowledge_point`
--

DROP TABLE IF EXISTS `knowledge_point`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_point` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识点ID',
  `course_id` bigint NOT NULL COMMENT '所属课程ID',
  `point_name` varchar(50) NOT NULL COMMENT '知识点名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父知识点ID（0为顶级）',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB AUTO_INCREMENT=98 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识点表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `knowledge_point`
--

LOCK TABLES `knowledge_point` WRITE;
/*!40000 ALTER TABLE `knowledge_point` DISABLE KEYS */;
INSERT INTO `knowledge_point` VALUES (1,1,'基础语法',0,1),(2,1,'面向对象',0,2),(3,1,'集合框架',0,3),(4,1,'异常处理',0,4),(5,1,'IO 与多线程',0,5),(6,2,'基础语法',0,1),(7,2,'数据类型与结构',0,2),(8,2,'函数与模块',0,3),(9,2,'面向对象',0,4),(10,2,'常用库',0,5),(11,3,'SQL 基础',0,1),(12,3,'查询与连接',0,2),(13,3,'索引与优化',0,3),(14,3,'事务与锁',0,4),(15,3,'数据库设计',0,5),(16,4,'OSI 与 TCP/IP',0,1),(17,4,'HTTP 协议',0,2),(18,4,'DNS 与 DHCP',0,3),(19,4,'网络安全',0,4),(20,5,'HTML 基础',0,1),(21,5,'CSS 布局',0,2),(22,5,'JavaScript 核心',0,3),(23,5,'Vue 框架',0,4),(24,6,'线性表',0,1),(25,6,'栈与队列',0,2),(26,6,'树与二叉树',0,3),(27,6,'图',0,4),(28,6,'排序与查找',0,5),(67,7,'信息系统基础知识',0,1),(68,7,'MIS 体系结构',0,2),(69,7,'决策支持系统 DSS',0,3),(70,7,'企业信息门户 EIP',0,4),(75,9,'用例图 Use Case',0,1),(76,9,'类图 Class Diagram',0,2),(77,9,'顺序图 Sequence Diagram',0,3),(78,9,'ER 模型与数据库设计',0,4),(79,10,'信息资源策划',0,1),(80,10,'信息标准化',0,2),(81,10,'信息文档管理',0,3),(82,11,'B2C/B2B 商业模式',0,1),(83,11,'电子支付方式',0,2),(84,11,'网络营销与 O2O',0,3),(85,11,'电商物流管理',0,4),(89,26,'函数的概念及常见函数',0,0),(90,26,'极限、连续概念',0,0),(91,26,'导数',0,0),(92,27,'矩阵及其运算',0,0),(93,27,'行列式',0,0),(94,27,'向量的内积',0,0),(95,28,'随机事件与概率',0,0),(96,28,'随机变量及分布',0,0),(97,28,'期望与方差',0,0);
/*!40000 ALTER TABLE `knowledge_point` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `paper_question`
--

DROP TABLE IF EXISTS `paper_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `paper_question` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `paper_id` bigint NOT NULL COMMENT '试卷ID',
  `question_id` bigint NOT NULL COMMENT '试题ID',
  `score` decimal(5,1) NOT NULL COMMENT '该题分值',
  `sort_order` int DEFAULT '0' COMMENT '题目顺序',
  PRIMARY KEY (`id`),
  KEY `idx_paper_id` (`paper_id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试卷题目关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `paper_question`
--

LOCK TABLES `paper_question` WRITE;
/*!40000 ALTER TABLE `paper_question` DISABLE KEYS */;
INSERT INTO `paper_question` VALUES (5,3,1,2.0,1),(6,3,3,3.0,2),(7,3,5,2.0,3),(8,3,7,2.0,4);
/*!40000 ALTER TABLE `paper_question` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `question`
--

DROP TABLE IF EXISTS `question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '试题ID',
  `course_id` bigint NOT NULL COMMENT '所属课程ID',
  `question_type` tinyint NOT NULL COMMENT '题型：1单选 2多选 3判断 4填空 5简答',
  `content` text NOT NULL COMMENT '题干内容',
  `options` text COMMENT '选项JSON数组，仅单选/多选题使用',
  `answer` text COMMENT '正确答案（判断题存T/F，填空题存答案，简答题存参考答案）',
  `analysis` text COMMENT '答案解析',
  `difficulty` tinyint NOT NULL DEFAULT '2' COMMENT '难度：1简单 2中等 3困难',
  `knowledge_id` bigint DEFAULT NULL COMMENT '关联知识点ID',
  `score` decimal(5,1) NOT NULL DEFAULT '2.0' COMMENT '默认分值',
  `creator_id` bigint NOT NULL COMMENT '创建人ID（教师）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_type` (`course_id`,`question_type`),
  KEY `idx_knowledge` (`knowledge_id`),
  KEY `idx_difficulty` (`difficulty`)
) ENGINE=InnoDB AUTO_INCREMENT=170 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试题表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `question`
--

LOCK TABLES `question` WRITE;
/*!40000 ALTER TABLE `question` DISABLE KEYS */;
INSERT INTO `question` VALUES (1,1,1,'下列哪一项不是 Java 的基本数据类型？','[\"int\", \"char\", \"String\", \"boolean\"]','C','Java 基本数据类型有 8 种，String 是引用类型。',1,1,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(2,1,1,'关于 Java 中 static 关键字，错误的说法是？','[\"static 方法可通过类名直接调用\", \"static 变量在内存中只有一份\", \"static 方法中可以访问非 static 实例变量\", \"static 代码块在类加载时执行\"]','C','static 方法中不能直接访问非 static 实例变量。',2,2,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(3,1,2,'下列关于 Java 集合框架的说法，正确的有？','[\"List 接口允许元素重复\", \"Set 接口不允许元素重复\", \"Map 是键值对集合，键不允许重复\", \"所有集合都线程安全\"]','A,B,C','D 错误：常用集合都是非线程安全的。',2,3,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(4,1,2,'下列哪些情况会导致 Java 抛出异常？','[\"除数为零\", \"数组下标越界\", \"对象引用为 null 时调用方法\", \"整数除法结果溢出\"]','B,C','整数溢出不会抛异常，结果会默默回绕。',3,4,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(5,1,3,'Java 中的 String 是不可变对象，每次修改都会创建新对象。',NULL,'T','String 内部用 final 存储，修改返回新对象。',1,1,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(6,1,3,'Java 中一个类可以实现多个接口，但只能继承一个父类。',NULL,'T','Java 是单继承多实现。',2,2,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(7,1,4,'Java 中用于定义常量的关键字是 ____。',NULL,'final','final 修饰的变量只能赋值一次。',1,1,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(8,1,4,'Java 集合中线程安全的类是 ____（提示：5 个字母）。',NULL,'Vector','Vector 是 JDK 中早期的线程安全 List。',2,3,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(9,1,5,'请简述 Java 中 final、finally、finalize 的区别。',NULL,'final 修饰类/方法/变量，表示不可变；finally 是 try-catch 中无论是否异常都会执行的代码块；finalize 是 Object 方法，GC 回收对象前调用。','三者作用完全不同。',2,1,5.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(10,1,5,'请简述 Java 中的值传递与引用传递。',NULL,'Java 只有值传递。方法得到的是参数副本，对象类型副本是引用的副本，所以方法内修改对象属性会影响原对象，但修改引用不会改变原引用。','考察对 Java 内存模型的理解。',3,2,5.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(11,2,1,'Python 中用于定义函数的关键字是？','[\"function\", \"def\", \"func\", \"define\"]','B','Python 使用 def 关键字定义函数。',1,6,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(12,2,1,'下列哪种数据类型在 Python 中是不可变的？','[\"list\", \"dict\", \"tuple\", \"set\"]','C','不可变类型：int、float、str、tuple。',1,7,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(13,2,1,'Python 中输出 hello 的正确语法是？','[\"echo hello\", \"print(hello)\", \"System.out.println\", \"console.log\"]','B','Python 3 使用 print() 函数输出。',1,6,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(14,2,1,'列表推导式 [x*x for x in range(5)] 的结果是？','[\"0,1,2,3,4\", \"1,4,9,16,25\", \"0,1,4,9,16\", \"0,0,0,0,0\"]','C','range(5) 生成 0~4，分别平方得 0,1,4,9,16。',2,7,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(15,2,1,'Python 中读取文件全部内容的方法是？','[\"file.read()\", \"file.readAll()\", \"file.getContent()\", \"file.input()\"]','A','open() 返回文件对象，read() 读取全部内容。',2,8,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(16,2,2,'下列属于 Python 面向对象特性的是？','[\"封装\", \"继承\", \"多态\", \"多重继承\"]','A,B,C,D','Python 全面支持面向对象四大特性。',2,9,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(17,2,2,'下列哪些是 Python 常用第三方库？','[\"requests\", \"numpy\", \"pandas\", \"jQuery\"]','A,B,C','jQuery 是 JavaScript 库。',2,10,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(18,2,3,'Python 中字典的键必须是不可变类型。',NULL,'T','字典键必须可哈希。',1,7,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(19,2,3,'Python 2 和 Python 3 的 print 语法完全相同。',NULL,'F','Python 2 print 是语句，Python 3 是函数。',1,6,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(20,2,5,'请简述 Python 中深拷贝和浅拷贝的区别。',NULL,'浅拷贝只复制对象引用，嵌套对象仍共享；深拷贝递归复制所有层级。copy.copy() 浅拷贝，copy.deepcopy() 深拷贝。','考察对 Python 内存模型的理解。',3,7,5.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(21,3,1,'MySQL 中用于查询数据的关键字是？','[\"SELECT\", \"FIND\", \"GET\", \"QUERY\"]','A','SQL 标准查询关键字就是 SELECT。',1,11,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(22,3,1,'下列哪种 JOIN 会返回左表全部记录，即使右表无匹配？','[\"INNER JOIN\", \"LEFT JOIN\", \"RIGHT JOIN\", \"CROSS JOIN\"]','B','LEFT JOIN 以左表为主，无匹配时右表补 NULL。',2,12,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(23,3,1,'MySQL 默认的事务隔离级别是？','[\"READ UNCOMMITTED\", \"READ COMMITTED\", \"REPEATABLE READ\", \"SERIALIZABLE\"]','C','MySQL InnoDB 默认 REPEATABLE READ。',2,14,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(24,3,1,'以下哪个字段适合建立索引？','[\"性别取值少\", \"频繁出现在 WHERE 条件\", \"大文本字段\", \"经常更新的字段\"]','B','高区分度+频繁查询字段建索引收益最大。',2,13,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(25,3,1,'数据库三大范式的主要目的是？','[\"加快查询速度\", \"减少数据冗余\", \"节省存储空间\", \"提高并发能力\"]','B','范式核心是消除数据冗余。',2,15,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(26,3,2,'下列属于 DDL 语句的有？','[\"CREATE TABLE\", \"INSERT INTO\", \"ALTER TABLE\", \"DROP TABLE\"]','A,C,D','DDL 是数据定义语言，INSERT 属于 DML。',2,11,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(27,3,2,'MySQL 中常见的存储引擎有？','[\"InnoDB\", \"MyISAM\", \"Memory\", \"Oracle\"]','A,B,C','Oracle 是数据库产品，不是 MySQL 存储引擎。',2,11,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(28,3,3,'MySQL 中索引一定能提高查询速度。',NULL,'F','索引在数据量小时不一定快，会降低写入性能。',2,13,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(29,3,3,'WHERE 子句中 LIKE \"abc%\" 可以使用索引。',NULL,'T','前缀匹配可以使用索引，后缀匹配不行。',2,13,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(30,3,5,'请简述 MySQL InnoDB 事务的 ACID 特性。',NULL,'原子性：事务要么全成功要么全失败；一致性：事务前后数据满足约束；隔离性：并发事务互不干扰；持久性：提交后永久生效。','ACID 是事务标准答案。',3,14,5.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(31,4,1,'OSI 七层模型中，负责路由选择的是？','[\"物理层\", \"数据链路层\", \"网络层\", \"传输层\"]','C','网络层（IP 协议）负责路由选择。',2,16,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(32,4,1,'HTTP 协议默认端口号是？','[\"21\", \"22\", \"80\", \"443\"]','C','HTTP=80，HTTPS=443。',1,17,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(33,4,1,'TCP 三次握手中，第二次握手发送的标志位是？','[\"SYN\", \"ACK\", \"SYN+ACK\", \"FIN\"]','C','第二次握手服务端同时发送 SYN 和 ACK。',2,16,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(34,4,1,'DNS 默认使用哪个端口？','[\"21\", \"53\", \"80\", \"443\"]','B','DNS 默认 UDP 53 端口。',2,18,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(35,4,1,'下列哪种攻击属于 SQL 注入类？','[\"XSS\", \"CSRF\", \"SQL Injection\", \"DDoS\"]','C','SQL 注入通过构造恶意 SQL 读取数据库。',1,19,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(36,4,2,'下列属于 TCP 协议特点的是？','[\"面向连接\", \"可靠传输\", \"无序到达\", \"拥塞控制\"]','A,B,D','TCP 面向连接、可靠、有序；UDP 才可能无序。',2,16,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(37,4,3,'HTTP 是无状态协议。',NULL,'T','HTTP 本身不保存状态，需借助 Cookie/Session/Token。',1,17,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(38,4,5,'请简述 TCP 三次握手的过程。',NULL,'第一次：客户端发送 SYN=1, seq=x。第二次：服务端回复 SYN=1, ACK=1。第三次：客户端发送 ACK=1，双方进入 ESTABLISHED。','三次握手是高频考点。',3,16,5.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(39,5,1,'HTML 中用于定义超链接的标签是？','[\"<a>\", \"<link>\", \"<href>\", \"<url>\"]','A','<a href=...> 是超链接标签。',1,20,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(40,5,1,'CSS 中设置元素文本颜色的属性是？','[\"font-color\", \"text-color\", \"color\", \"background-color\"]','C','color 设置文本颜色。',1,21,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(41,5,1,'JavaScript 中声明常量的关键字是？','[\"var\", \"let\", \"const\", \"static\"]','C','const 声明常量。',1,22,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(42,5,1,'Vue 3 中用于创建响应式对象的 API 是？','[\"reactive()\", \"ref()\", \"computed()\", \"以上都是\"]','D','reactive/ref/computed 都可创建响应式数据。',2,23,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(43,5,1,'关于 box-sizing: border-box，正确的是？','[\"width 只包含 content\", \"width 包含 content+padding+border\", \"padding 不计入 width\", \"只在 IE 生效\"]','B','border-box 让 width 包含 padding 和 border。',2,21,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(44,5,2,'下列属于 CSS 布局方式的是？','[\"Flexbox\", \"Grid\", \"Float\", \"Position\"]','A,B,C,D','都是 CSS 常用布局手段。',2,21,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(45,5,2,'Vue 3 的新特性包括？','[\"Composition API\", \"Teleport\", \"Fragments\", \"移除 Options API\"]','A,B,C','Vue 3 保留 Options API 并新增其他特性。',2,23,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(46,5,5,'请简述 Vue 3 中 ref 和 reactive 的区别。',NULL,'ref 包装基本类型和对象，返回带 .value 的响应式对象；reactive 包装对象返回 Proxy 代理。模板中 ref 自动解包。','考察对 Vue 3 响应式系统的理解。',3,23,5.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(47,6,1,'栈的特点是？','[\"先进先出\", \"后进先出\", \"随机存取\", \"优先级排序\"]','B','栈是 LIFO 后进先出。',1,25,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(48,6,1,'二分查找要求数据是？','[\"无序数组\", \"有序数组\", \"链表\", \"任意结构\"]','B','二分查找前提是有序数组。',1,28,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(49,6,1,'一棵完全二叉树的第 5 层最多有多少节点？','[\"8\", \"16\", \"32\", \"15\"]','B','第 k 层最多 2 的 k-1 次方个节点。',2,26,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(50,6,1,'下列排序算法中，时间复杂度最稳定为 O(n log n) 的是？','[\"快速排序\", \"冒泡排序\", \"归并排序\", \"堆排序\"]','C','归并最坏/平均/最好都是 O(n log n)。',2,28,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(51,6,1,'图的深度优先遍历通常借助哪种数据结构？','[\"队列\", \"栈\", \"堆\", \"数组\"]','B','DFS 用栈，BFS 用队列。',2,27,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(52,6,2,'下列属于线性表的是？','[\"数组\", \"链表\", \"栈\", \"队列\"]','A,B,C,D','栈和队列是受限的线性表。',1,24,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(53,6,2,'关于哈希表，正确的有？','[\"平均查找 O(1)\", \"会发生哈希冲突\", \"适合存储有序数据\", \"常用拉链法解决冲突\"]','A,B,D','哈希表不维护顺序。',3,28,3.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(54,6,3,'链表的插入和删除时间复杂度总是 O(1)。',NULL,'F','需要先遍历找位置，平均 O(n)。',2,24,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(55,6,3,'二叉搜索树的中序遍历结果是有序的。',NULL,'T','BST 性质决定中序遍历得到升序序列。',2,26,2.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(56,6,5,'请简述快速排序的基本思想及平均/最坏时间复杂度。',NULL,'选取基准元素，将数组分为小于和大于基准两部分，递归排序。平均 O(n log n)，最坏 O(n²)。','快排是面试最高频算法题。',3,28,5.0,1,'2026-08-18 21:34:06','2026-08-18 21:34:06'),(57,1,1,'下列关于 Java 构造方法的说法，正确的是？','[\"构造方法名必须与类名相同\",\"构造方法必须有返回值类型\",\"一个类只能有一个构造方法\",\"构造方法不能重载\"]','A','构造方法没有返回值类型（连 void 都没有）；一个类可以有多个重载的构造方法。',1,1,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(58,1,3,'Java 中 String 类是 final 类，不能被继承。',NULL,'T','String 被 final 修饰，不可继承，保证字符串的不可变性和安全性。',2,1,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(59,1,4,'Java 中用于终止当前循环整体（跳出到循环外）的关键字是 ______。',NULL,'break','break 终止整个循环；continue 只跳过本次循环。',1,1,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(60,2,1,'下列关于 Python 列表和元组的说法，正确的是？','[\"列表是不可变序列，元组是可变序列\",\"列表用 [] 定义，元组用 () 定义\",\"两者都不支持索引访问\",\"元组支持 append 方法\"]','B','列表 [] 可变，元组 () 不可变；两者都支持索引访问；元组没有 append 方法。',1,7,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(61,2,2,'下列属于 Python 内置数据结构的有？','[\"list\",\"tuple\",\"dict\",\"set\"]','A,B,C,D','list、tuple、dict、set 都是 Python 内置数据结构。',1,7,3.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(62,2,3,'Python 中使用 # 号表示单行注释。',NULL,'T','Python 单行注释用 #，多行注释可用三引号字符串。',1,6,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(63,3,1,'在 MySQL 中，用于去除查询结果中重复行的关键字是？','[\"UNIQUE\",\"DISTINCT\",\"GROUP\",\"LIMIT\"]','B','SELECT DISTINCT 列名 可去除重复行。',1,11,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(64,3,2,'下列关于 MySQL 索引的说法，正确的有？','[\"主键索引会自动创建\",\"索引可以提高查询速度但会降低插入/更新速度\",\"一个表只能创建一个索引\",\"唯一索引的列值不能重复\"]','A,B,D','一个表可以创建多个索引，C 错误。',2,13,3.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(65,3,5,'请简述事务的 ACID 特性分别是什么含义。',NULL,'原子性(Atomicity)：事务中的操作要么全部成功要么全部失败回滚；一致性(Consistency)：事务前后数据完整性约束不被破坏；隔离性(Isolation)：并发事务之间互不干扰；持久性(Durability)：事务提交后对数据的修改永久保存。','A原子性指全部成功或全部回滚；C一致性指数据完整性不被破坏；I隔离性指并发事务互不干扰；D持久性指提交后永久生效即可。',2,14,5.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(66,4,1,'HTTP 协议默认使用的端口号是？','[\"21\",\"80\",\"443\",\"8080\"]','B','HTTP 默认 80 端口；HTTPS 默认 443 端口；21 是 FTP。',1,17,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(67,4,1,'在 OSI 七层模型中，负责路由选择的是哪一层？','[\"物理层\",\"数据链路层\",\"网络层\",\"传输层\"]','C','网络层负责路由选择和逻辑寻址（IP 协议工作在此层）。',2,16,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(68,4,2,'下列属于 TCP 协议特点的有？','[\"面向连接\",\"可靠传输\",\"全双工通信\",\"无序不重传\"]','A,B,C','TCP 面向连接、可靠、全双工；D 是 UDP 的特点。',2,16,3.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(69,4,4,'IPv4 地址由 ______ 位二进制数组成。',NULL,'32','IPv4 是 32 位，通常写成 4 个十进制数（点分十进制）；IPv6 是 128 位。',1,16,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(70,5,1,'在 CSS 中，下列哪个属性用于设置文本颜色？','[\"background-color\",\"color\",\"font-color\",\"text-color\"]','B','color 设置文本颜色；background-color 设置背景色；不存在 font-color/text-color。',1,21,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(71,5,2,'下列关于 Vue 3 的说法，正确的有？','[\"组合式 API(Composition API) 是 Vue 3 新增特性\",\"Vue 3 支持多个根节点(片段)\",\"v-model 只能用在 input 元素上\",\"ref 可以创建响应式数据\"]','A,B,D','Vue 3 中 v-model 可以用在自定义组件上，C 错误。',2,23,3.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(72,5,3,'HTML 中 <br> 标签的作用是插入一个换行。',NULL,'T','<br> 是换行标签，无需闭合；<p> 是段落标签。',1,20,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(73,5,4,'在 JavaScript 中，声明一个只读常量应使用关键字 ______。',NULL,'const','const 声明常量，不可重新赋值；let/var 声明变量。',1,22,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(74,6,1,'对一个有 n 个元素的数组进行冒泡排序，最坏情况下的时间复杂度是？','[\"O(n)\",\"O(n log n)\",\"O(n²)\",\"O(log n)\"]','C','冒泡排序最坏（逆序）情况比较次数为 n(n-1)/2，即 O(n²)。',2,28,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(75,6,3,'二叉搜索树的中序遍历结果是一个有序序列。',NULL,'T','二叉搜索树左子树<根<右子树，中序遍历（左根右）恰好升序。',2,26,2.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(76,6,5,'请简述栈和队列的区别，并各举一个实际应用场景。',NULL,'栈是后进先出(LIFO)结构，只能在一端（栈顶）插入和删除；队列是先进先出(FIFO)结构，一端入队一端出队。栈的应用：函数调用栈、浏览器后退、括号匹配；队列的应用：打印任务排队、消息队列、BFS广度优先搜索。','要点：LIFO vs FIFO、栈单端操作、队列双端操作、各举一例即可。',2,25,5.0,1,'2026-08-21 02:20:08','2026-08-21 02:20:08'),(133,7,1,'管理信息系统（MIS）的主要功能不包括以下哪项？','[\"数据存储\",\"数据处理\",\"自动决策\",\"信息报告\"]','C','MIS 能收集、加工、储存、报告，但不能自动决策（自动决策属智能信息系统）。',1,67,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(134,7,2,'下列属于企业信息系统中“支撑部门级管理”的是？','[\"事务处理系统 TPS\",\"管理信息系统 MIS\",\"决策支持系统 DSS\",\"知识管理系统 KMS\"]','B','MIS 为部门级管理提供信息支持。',2,68,3.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(135,7,1,'决策支持系统（DSS）不包含以下哪部分？','[\"数据基址\",\"模型基址\",\"求解机\",\"用户接口\"]','C','DSS 由数据基址、模型基址、用户接口组成，求解机不是标准组成。',1,69,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(136,7,4,'管理信息系统的 4 大基本功能中，向用户提供信息的是 ______ 功能。',NULL,'信息报告','MIS 基本功能：输入、储存、处理、输出（信息报告）。',1,67,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(137,7,3,'管理信息系统能完全代替人类进行战术决策。',NULL,'F','MIS 只能辅助人类决策，不能完全代替。',3,68,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(138,7,5,'简述管理信息系统在企业中的作用。',NULL,'通过收集、加工、储存、传递与显示企业信息，为计划、组织、监控、控制提供决策依据，提高管理效率，优化资源配置。','答出“辅助决策/信息报告/提高效率”等关键点即可。',2,67,5.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(139,7,2,'关于企业信息门户（EIP）的描述，正确的有？','[\"以员工为中心整合信息\",\"提供个性化工作台视图\",\"仅面向高层管理\",\"支持跨组织协同\"]','A,B,D','EIP 以员工为中心、个性化视图、跨组织协同；C 错（面向全员）。',3,70,3.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(146,9,1,'用例图的元素不包括以下哪项？','[\"参与者\",\"用例\",\"系统边界\",\"数据流\"]','D','用例图元素：参与者、用例、系统边界；数据流属于数据流图。',1,75,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(147,9,2,'UML 类图中，类与类间关系包括以下？','[\"聚合\",\"组合\",\"继承\",\"依赖\"]','D','聚合、组合、继承、依赖均为类间关系；题为“不包括”，但四者皆是。',2,76,3.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(148,9,2,'关于 ER 模型的描述，正确的有？','[\"E-R图表示实体及关系\",\"属性可关联实体或关系\",\"主键唯一标识实体\",\"E-R图不能表示约束\"]','A,B,C','A、B、C 正确；D 错（可表示基数约束）。',3,78,3.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(149,9,4,'在用例设计中，一条用例通常对应 ______ 个事务。',NULL,'一个','一个用例完成一个功能事务。',1,75,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(150,9,3,'类图中，一个类通常由属性、方法（含构造方法）组成。',NULL,'T','正确。',2,76,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(151,9,5,'简述信息系统分析与设计的基本流程。',NULL,'需求分析 → 概要设计 → 详细设计 → 实现 → 测试 → 部署与维护。','答出需求-设计-实现-测试-部署即可。',2,76,5.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(152,10,1,'信息资源管理的核心内容不包括以下哪项？','[\"信息策划\",\"信息开发\",\"信息存储\",\"信息销毁\"]','D','信息资源管理核心：策划、开发、利用、维护；销毁属生命周期终结，非核心。',2,79,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(153,10,2,'关于信息标准化的描述，正确的有？','[\"是信息资源管理基础\",\"含数据标准与接口标准\",\"有利于信息共享\",\"仅适用于软件开发\"]','A,B,C','D 错，标准化广泛用于数据交换、互操作。',2,80,3.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(154,10,4,'信息文档管理中，文档的固定标识属性是 ______。',NULL,'文档编号','编号唯一固定；题名/主题/内容可变更。',2,81,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(155,10,3,'信息资源的数字化是信息资源管理的基础。',NULL,'T','数字化是模拟信息向数字信息转换，有其基础性作用。',2,79,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(156,11,1,'以下属于 B2C 电子商务模式的是？','[\"京东 当当\",\"阿里巴巴 1688\",\"苏宁易购\",\"以上都是\"]','A','京东、当当、苏宁易购是 B2C；阿里 1688 是 B2B。',1,82,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(157,11,2,'当前国内主要移动支付方式包括？','[\"支付宝\",\"微信支付\",\"银联二维码\",\"以上都是\"]','D','三者均为主要移动支付方式。',1,83,3.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(158,11,1,'O2O 电商模式的核心是什么？','[\"线上线下整合\",\"纯线上交易\",\"纯线下店铺\",\"社交购物\"]','A','O2O 将线上交易与线下服务整合。',2,84,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(159,11,4,'电商物流中 “最后一公里”指 ______。',NULL,'从配送站到消费者手的配送环节','末段配送，成本高、耗时长。',3,85,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(160,11,3,'O2O 电商强调线上线下的整合服务。',NULL,'T','O2O = Online-to-Offline。',3,84,2.0,1,'2026-08-21 23:10:01','2026-08-21 23:10:01'),(161,26,3,'函数 f(x) 在 x=a 处连续且存在导数，那么 f(x) 在 x=a 处一定可导。','对|错','F','连续不可推存在导数， |x| 在 0 处连续但不可导。',1,89,2.0,1,'2026-08-22 00:06:00','2026-08-22 00:06:00'),(162,26,4,'求 f(x) = x^3 + 2x 在 x=1 处的导数值。',NULL,'5','f\'(x) = 3x^2 + 2，代入 x=1 得 5。',1,91,2.0,1,'2026-08-22 00:06:00','2026-08-22 00:06:00'),(163,26,5,'简述函数在一点处可导与连续的关系，并举一例说明。',NULL,'可导必连续，反之不成立。例如 f(x)=|x| 在 x=0 连续但不可导。','可导函数在该点必连续，连续函数在该点不一定可导。',2,89,5.0,1,'2026-08-22 00:06:00','2026-08-22 00:06:00'),(164,27,3,'两个 3 阶矩阵相乘，结果的秩一定不大于其中任一个矩阵的秩。','对|错','T','秩不超过因数矩阵的秩。',1,92,2.0,1,'2026-08-22 00:06:00','2026-08-22 00:06:00'),(165,27,4,'已知 2 阶行列式 |a b; c d| = 5，求 |2a 2b; 2c 2d| 的值。',NULL,'20','行列式的每一行提出因子 2，2^2 × 5 = 20。',1,93,2.0,1,'2026-08-22 00:06:00','2026-08-22 00:06:00'),(166,27,5,'解释 n 维向量点积的几何意义。',NULL,'点积 = 两向量模的乘积 × 夹角余弦值。','a·b = |a||b|cosθ。',2,94,5.0,1,'2026-08-22 00:06:00','2026-08-22 00:06:00'),(167,28,3,'互斥事件（P(AB)=0）一定是相互独立的。','对|错','F','互斥事件不满足 P(AB)=P(A)P(B)。',1,95,2.0,1,'2026-08-22 00:06:00','2026-08-22 00:06:00'),(168,28,4,'泊松分布参数 λ 的概率解释。',NULL,'单位时间或单位面积内事件发生的平均次数','泊松分布刻画单位区间事件发生的平均次数 λ。',1,96,2.0,1,'2026-08-22 00:06:00','2026-08-22 00:06:00'),(169,28,5,'写出条件概率 P(B|A) 的定义，并说明适用条件。',NULL,'P(B|A)=P(AB)/P(A)，需 P(A)>0。','条件概率 = 联事件概率 / 给定事件概率。',2,95,5.0,1,'2026-08-22 00:06:00','2026-08-22 00:06:00');
/*!40000 ALTER TABLE `question` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `question_option`
--

DROP TABLE IF EXISTS `question_option`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_option` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '选项ID',
  `question_id` bigint NOT NULL COMMENT '所属试题ID',
  `option_label` varchar(10) NOT NULL COMMENT '选项编号（A/B/C/D/T/F）',
  `option_content` text NOT NULL COMMENT '选项内容',
  `is_correct` tinyint NOT NULL DEFAULT '0' COMMENT '是否正确答案：0否 1是',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  PRIMARY KEY (`id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='试题选项表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `question_option`
--

LOCK TABLES `question_option` WRITE;
/*!40000 ALTER TABLE `question_option` DISABLE KEYS */;
/*!40000 ALTER TABLE `question_option` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_class`
--

DROP TABLE IF EXISTS `sys_class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_class` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '班级ID',
  `class_name` varchar(50) NOT NULL COMMENT '班级名称',
  `grade` varchar(20) DEFAULT NULL COMMENT '年级（如2024级）',
  `department` varchar(50) DEFAULT NULL COMMENT '院系',
  `major` varchar(100) DEFAULT NULL COMMENT '专业',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班级表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_class`
--

LOCK TABLES `sys_class` WRITE;
/*!40000 ALTER TABLE `sys_class` DISABLE KEYS */;
INSERT INTO `sys_class` VALUES (15,'23005801','2023级','信息管理与信息系统',NULL,'2026-08-22 00:04:09'),(16,'23005802','2023级','信息管理与信息系统',NULL,'2026-08-22 00:04:09'),(17,'23005803','2023级','信息管理与信息系统',NULL,'2026-08-22 00:04:09'),(18,'23005804','2023级','信息管理与信息系统',NULL,'2026-08-22 00:04:09');
/*!40000 ALTER TABLE `sys_class` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_course`
--

DROP TABLE IF EXISTS `sys_course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_course` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  `course_name` varchar(50) NOT NULL COMMENT '课程名称',
  `course_code` varchar(20) DEFAULT NULL COMMENT '课程编码',
  `course_type` varchar(20) DEFAULT NULL COMMENT '课程类型',
  `description` varchar(200) DEFAULT NULL COMMENT '课程描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_course`
--

LOCK TABLES `sys_course` WRITE;
/*!40000 ALTER TABLE `sys_course` DISABLE KEYS */;
INSERT INTO `sys_course` VALUES (1,'Java 程序设计基础','JAVA101','面向对象编程基础语法与核心特性','2026-08-18 21:34:06'),(2,'Python 程序设计','PY101','Python 语法、数据结构与常用库','2026-08-18 21:34:06'),(3,'MySQL 数据库','DB101','关系型数据库 SQL 与设计范式','2026-08-18 21:34:06'),(4,'计算机网络','NET101','TCP/IP、HTTP 与网络安全基础','2026-08-18 21:34:06'),(5,'Web 前端开发','WEB101','HTML、CSS、JavaScript 与 Vue 基础','2026-08-18 21:34:06'),(6,'数据结构与算法','DS101','线性表、树、图与常用算法','2026-08-18 21:34:06'),(7,'管理信息系统','MIS101','管理信息系统原理、体系结构与应用','2026-08-21 23:05:59'),(9,'信息系统分析与设计','SAD101','系统分析方法、UML用例建模','2026-08-21 23:05:59'),(10,'信息资源管理','IRM101','信息资源策划、开发与维护','2026-08-21 23:05:59'),(11,'电子商务','EC101','电商平台、支付与营销模式','2026-08-21 23:05:59'),(26,'高等数学','MATH101','高等数学：函数、极限、导数、积分等基础理论','2026-08-22 00:04:09'),(27,'线性代数','LA101','线性代数：矩阵、行列式、向量空间等','2026-08-22 00:04:09'),(28,'概率论','PRO101','概率论：随机事件、随机变量、分布、期望等','2026-08-22 00:04:09');
/*!40000 ALTER TABLE `sys_course` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名/学号/工号',
  `password` varchar(100) NOT NULL COMMENT '密码（BCrypt加密）',
  `real_name` varchar(50) NOT NULL COMMENT '真实姓名',
  `role` tinyint NOT NULL DEFAULT '0' COMMENT '角色：0学生 1教师 2管理员',
  `openid` varchar(64) DEFAULT NULL COMMENT '微信openid（学生小程序登录用）',
  `class_id` bigint DEFAULT NULL COMMENT '班级ID（学生）',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0禁用 1正常',
  `wrong_auto_remove` tinyint NOT NULL DEFAULT '0' COMMENT '错题本自动移除开关：0关 1开',
  `wrong_remove_threshold` int NOT NULL DEFAULT '3' COMMENT '做对几次后移出错题本（1 或 3）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_openid` (`openid`),
  KEY `idx_class_id` (`class_id`)
) ENGINE=InnoDB AUTO_INCREMENT=125 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'admin','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','管理员',2,NULL,NULL,NULL,NULL,1,0,3,'2026-08-18 18:14:56','2026-08-22 02:48:58'),(2,'2300580201','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','崔三娘',0,NULL,16,NULL,NULL,1,1,3,'2026-08-18 21:34:06','2026-08-22 14:07:57'),(3,'2300580202','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','顾清寒',0,NULL,16,NULL,NULL,1,0,3,'2026-08-18 21:34:06','2026-08-22 02:48:58'),(4,'2300580203','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','哈迪',0,NULL,16,NULL,NULL,1,0,3,'2026-08-18 21:34:06','2026-08-22 02:48:58'),(5,'2300580204','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','胡桃',0,NULL,16,NULL,NULL,1,0,3,'2026-08-18 21:34:06','2026-08-22 02:48:58'),(6,'2300580205','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','胡为',0,NULL,16,NULL,NULL,1,0,3,'2026-08-18 21:34:06','2026-08-22 02:48:58'),(7,'2300580206','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','迦南',0,NULL,16,NULL,NULL,1,0,3,'2026-08-18 21:34:06','2026-08-22 02:48:58'),(8,'teacher01','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','陈平安',1,NULL,NULL,NULL,NULL,1,0,3,'2026-08-18 22:26:51','2026-08-22 02:48:58'),(9,'teacher02','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','光头强',1,NULL,NULL,NULL,NULL,1,0,3,'2026-08-18 22:26:51','2026-08-22 02:48:58'),(10,'2300580101','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','白浩芝',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(11,'2300580102','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','陈巧倩',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(12,'2300580103','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','董萱儿',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(13,'2300580104','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','韩立',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(14,'2300580105','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','菡云芝',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(15,'2300580106','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','红拂',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(16,'2300580107','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','厉飞雨',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(17,'2300580108','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','李化元',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(18,'2300580109','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','令狐',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(19,'2300580110','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','凌玉灵',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(20,'2300580111','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','柳玉',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(21,'2300580112','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','梅凝',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(22,'2300580113','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','墨彩环',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(23,'2300580114','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','墨居仁',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(24,'2300580115','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','慕沛灵',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(25,'2300580116','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','南宫婉',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(26,'2300580117','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','青易子',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(27,'2300580118','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','宋蒙',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(28,'2300580119','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','宋玉',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(29,'2300580120','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','啼魂',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(30,'2300580121','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','王蝉',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(31,'2300580122','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','万天明',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(32,'2300580123','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','文思月',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(33,'2300580124','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','温天仁',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(34,'2300580125','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','向之礼',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(35,'2300580126','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','玄骨',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(36,'2300580127','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','妍丽',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(37,'2300580128','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','银月',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(38,'2300580129','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','元瑶',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(39,'2300580130','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','张铁',0,NULL,15,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(40,'2300580207','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','季沧海',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(41,'2300580208','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','季莹莹',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(42,'2300580209','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','蓝梦',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(43,'2300580210','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','刘炼',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(44,'2300580211','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','宁红夜',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(45,'2300580212','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','齐铁嘴',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(46,'2300580213','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','沈妙',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(47,'2300580214','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','特木尔',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(48,'2300580215','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','天海',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(49,'2300580216','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','魏轻',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(50,'2300580217','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','无尘',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(51,'2300580218','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','吴二白',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(52,'2300580219','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','吴三省',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(53,'2300580220','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','武田信忠',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(54,'2300580221','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','吴邪',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(55,'2300580222','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','吴一穷',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(56,'2300580223','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','席拉',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(57,'2300580224','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','妖刀姬',0,NULL,16,NULL,NULL,1,1,3,'2026-08-22 00:10:20','2026-08-22 19:16:39'),(58,'2300580225','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','殷紫萍',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(59,'2300580226','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','岳山',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(60,'2300580227','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','玉玲珑',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(61,'2300580228','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','张起灵',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(62,'2300580229','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','张启山',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(63,'2300580230','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','张日山',0,NULL,16,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(64,'2300580301','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','白露',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(65,'2300580302','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','博识尊',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(66,'2300580303','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','不死途',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(67,'2300580304','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','长夜月',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(68,'2300580305','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','丹恒',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(69,'2300580306','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','焚风',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(70,'2300580307','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','符华',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(71,'2300580308','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','符玄',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(72,'2300580309','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','花火',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(73,'2300580310','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','黄泉',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(74,'2300580311','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','藿藿',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(75,'2300580312','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','杰帕德',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(76,'2300580313','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','镜流',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(77,'2300580314','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','景元',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(78,'2300580315','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','卡芙卡',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(79,'2300580316','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','流萤',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(80,'2300580317','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','李信',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(81,'2300580318','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','罗刹',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(82,'2300580319','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','千冶刃',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(83,'2300580320','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','青雀',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(84,'2300580321','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','三月七',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(85,'2300580322','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','藤荒',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(86,'2300580323','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','停云',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(87,'2300580324','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','托帕',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(88,'2300580325','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','瓦尔特',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(89,'2300580326','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','彦卿',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(90,'2300580327','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','爻光',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(91,'2300580328','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','银狼',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(92,'2300580329','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','真珠',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(93,'2300580330','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','知更鸟',0,NULL,17,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(94,'2300580401','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','白厄',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(95,'2300580402','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','飞霄',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(96,'2300580403','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','风堇',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(97,'2300580404','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','归寂',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(98,'2300580405','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','桂乃芬',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(99,'2300580406','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','黑塔',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(100,'2300580407','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','幻太子',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(101,'2300580408','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','火花',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(102,'2300580409','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','加拉赫',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(103,'2300580410','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','玲可',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(104,'2300580411','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','乱破',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(105,'2300580412','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','米沙',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(106,'2300580413','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','貊泽',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(107,'2300580414','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','那刻夏',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(108,'2300580415','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','纳努克',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(109,'2300580416','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','阮梅',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(110,'2300580417','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','萨姆',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(111,'2300580418','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','桑博',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(112,'2300580419','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','砂金',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(113,'2300580420','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','缇宝',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(114,'2300580421','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','铁幕',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(115,'2300580422','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','万敌',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(116,'2300580423','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','遐蝶',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(117,'2300580424','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','昔涟',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(118,'2300580425','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','希露瓦',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(119,'2300580426','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','星期日',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(120,'2300580427','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','雪衣',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(121,'2300580428','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','虚照',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(122,'2300580429','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','银枝',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(123,'2300580430','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','远坂凛',0,NULL,18,NULL,NULL,1,0,3,'2026-08-22 00:10:20','2026-08-22 02:48:58'),(124,'teacher03','$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq','叶枫',1,NULL,NULL,NULL,NULL,1,0,3,'2026-08-22 01:30:38','2026-08-22 02:48:58');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_names_backup_20260822`
--

DROP TABLE IF EXISTS `sys_user_names_backup_20260822`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_names_backup_20260822` (
  `id` bigint NOT NULL DEFAULT '0' COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名/学号/工号',
  `real_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '真实姓名',
  `role` tinyint NOT NULL DEFAULT '0' COMMENT '角色：0学生 1教师 2管理员'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_names_backup_20260822`
--

LOCK TABLES `sys_user_names_backup_20260822` WRITE;
/*!40000 ALTER TABLE `sys_user_names_backup_20260822` DISABLE KEYS */;
INSERT INTO `sys_user_names_backup_20260822` VALUES (1,'admin','管理员',2),(2,'2300580201','迦南',0),(3,'2300580202','胡桃',0),(4,'2300580203','季沧海',0),(5,'2300580204','天海',0),(6,'2300580205','宁红夜',0),(7,'2300580206','特木尔',0),(8,'teacher01','陈平安',1),(9,'teacher02','光头强',1),(10,'2300580101','韩立',0),(11,'2300580102','南宫婉',0),(12,'2300580103','墨居仁',0),(13,'2300580104','厉飞雨',0),(14,'2300580105','张铁',0),(15,'2300580106','墨彩环',0),(16,'2300580107','李化元',0),(17,'2300580108','红拂',0),(18,'2300580109','陈巧倩',0),(19,'2300580110','菡云芝',0),(20,'2300580111','董萱儿',0),(21,'2300580112','令狐',0),(22,'2300580113','向之礼',0),(23,'2300580114','王蝉',0),(24,'2300580115','温天仁',0),(25,'2300580116','慕沛灵',0),(26,'2300580117','玄骨',0),(27,'2300580118','宋蒙',0),(28,'2300580119','银月',0),(29,'2300580120','元瑶',0),(30,'2300580121','妍丽',0),(31,'2300580122','梅凝',0),(32,'2300580123','白浩芝',0),(33,'2300580124','文思月',0),(34,'2300580125','凌玉灵',0),(35,'2300580126','啼魂',0),(36,'2300580127','万天明',0),(37,'2300580128','青易子',0),(38,'2300580129','柳玉',0),(39,'2300580130','宋玉',0),(40,'2300580207','妖刀姬',0),(41,'2300580208','崔三娘',0),(42,'2300580209','岳山',0),(43,'2300580210','无尘',0),(44,'2300580211','顾清寒',0),(45,'2300580212','武田信忠',0),(46,'2300580213','殷紫萍',0),(47,'2300580214','沈妙',0),(48,'2300580215','胡为',0),(49,'2300580216','季莹莹',0),(50,'2300580217','玉玲珑',0),(51,'2300580218','哈迪',0),(52,'2300580219','魏轻',0),(53,'2300580220','刘炼',0),(54,'2300580221','席拉',0),(55,'2300580222','蓝梦',0),(56,'2300580223','张起灵',0),(57,'2300580224','张启山',0),(58,'2300580225','张日山',0),(59,'2300580226','吴一穷',0),(60,'2300580227','吴二白',0),(61,'2300580228','吴三省',0),(62,'2300580229','吴邪',0),(63,'2300580230','齐铁嘴',0),(64,'2300580301','李信',0),(65,'2300580302','三月七',0),(66,'2300580303','丹恒',0),(67,'2300580304','瓦尔特',0),(68,'2300580305','杰帕德',0),(69,'2300580306','爻光',0),(70,'2300580307','焚风',0),(71,'2300580308','长夜月',0),(72,'2300580309','藤荒',0),(73,'2300580310','符华',0),(74,'2300580311','景元',0),(75,'2300580312','彦卿',0),(76,'2300580313','停云',0),(77,'2300580314','白露',0),(78,'2300580315','符玄',0),(79,'2300580316','青雀',0),(80,'2300580317','博识尊',0),(81,'2300580318','罗刹',0),(82,'2300580319','千冶刃',0),(83,'2300580320','卡芙卡',0),(84,'2300580321','银狼',0),(85,'2300580322','镜流',0),(86,'2300580323','藿藿',0),(87,'2300580324','托帕',0),(88,'2300580325','花火',0),(89,'2300580326','黄泉',0),(90,'2300580327','不死途',0),(91,'2300580328','真珠',0),(92,'2300580329','知更鸟',0),(93,'2300580330','流萤',0),(94,'2300580401','白厄',0),(95,'2300580402','银枝',0),(96,'2300580403','砂金',0),(97,'2300580404','铁幕',0),(98,'2300580405','飞霄',0),(99,'2300580406','归寂',0),(100,'2300580407','加拉赫',0),(101,'2300580408','雪衣',0),(102,'2300580409','纳努克',0),(103,'2300580410','玲可',0),(104,'2300580411','虚照',0),(105,'2300580412','乱破',0),(106,'2300580413','星期日',0),(107,'2300580414','希露瓦',0),(108,'2300580415','黑塔',0),(109,'2300580416','远坂凛',0),(110,'2300580417','桑博',0),(111,'2300580418','桂乃芬',0),(112,'2300580419','米沙',0),(113,'2300580420','貊泽',0),(114,'2300580421','萨姆',0),(115,'2300580422','遐蝶',0),(116,'2300580423','阮梅',0),(117,'2300580424','幻太子',0),(118,'2300580425','风堇',0),(119,'2300580426','那刻夏',0),(120,'2300580427','火花',0),(121,'2300580428','昔涟',0),(122,'2300580429','缇宝',0),(123,'2300580430','万敌',0),(124,'teacher03','叶枫',1);
/*!40000 ALTER TABLE `sys_user_names_backup_20260822` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wrong_question`
--

DROP TABLE IF EXISTS `wrong_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wrong_question` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '错题ID',
  `user_id` bigint NOT NULL COMMENT '学生ID',
  `question_id` bigint NOT NULL COMMENT '试题ID',
  `exam_id` bigint DEFAULT NULL COMMENT '所属考试ID（归集来源）',
  `paper_id` bigint DEFAULT NULL COMMENT '试卷ID',
  `course_id` bigint DEFAULT NULL COMMENT '学科ID（冗余，方便按学科筛选）',
  `question_type` tinyint DEFAULT NULL COMMENT '题型：1=单选 2=多选 3=判断 4=填空 5=简答',
  `user_answer` text COMMENT '学生当时提交的（错误）答案',
  `correct_answer` text COMMENT '标准答案',
  `score` decimal(5,1) DEFAULT NULL COMMENT '该题分值',
  `record_id` bigint DEFAULT NULL COMMENT '关联考试记录ID',
  `wrong_count` int NOT NULL DEFAULT '1' COMMENT '错误次数',
  `correct_count` int NOT NULL DEFAULT '0' COMMENT '重做做对次数（达到阈值后自动移出错题本）',
  `last_wrong_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最近错误时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`,`question_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='错题本表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wrong_question`
--

LOCK TABLES `wrong_question` WRITE;
/*!40000 ALTER TABLE `wrong_question` DISABLE KEYS */;
INSERT INTO `wrong_question` VALUES (9,2,7,2,3,1,4,'wrong','final',2.0,16,1,0,'2026-08-19 14:12:31'),(10,57,14,NULL,NULL,2,1,'D','C',2.0,NULL,1,0,'2026-08-22 19:06:27'),(11,57,15,NULL,NULL,2,1,'D','A',2.0,NULL,1,0,'2026-08-22 19:06:30');
/*!40000 ALTER TABLE `wrong_question` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-22 19:21:58
