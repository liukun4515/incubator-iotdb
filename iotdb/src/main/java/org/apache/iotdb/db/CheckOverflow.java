package org.apache.iotdb.db;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.iotdb.db.engine.overflow.ioV2.OverflowResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckOverflow {

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckOverflow.class);

  public static void main(String[] args) {
    if (args.length > 0) {
      System.out.println("args:");
      for (String arg : args) {
        System.out.println(arg);
      }
    }

    if (args.length != 1) {
      System.out.println("Please the input the overflow data path: \n"
          + "mvn exec:java -Dexec.mainClass=\"org.apache.iotdb.db.checkoverflow\" -Dexec.args=\"path\"");
      System.exit(1);
    }
    // 遍历所有的overflow文件夹
    String overflowDirPath = args[0];
    File overflowDir = new File(overflowDirPath);
    File[] childrenDiroverflowDir = overflowDir.listFiles();

    for (File childDir : childrenDiroverflowDir) {
      try {
        recovery(childDir);
      } catch (IOException e) {
        LOGGER.error("Cath the IO Exception, Recovery the overflow dir: {}, the reason is {}.", childDir.getPath(), e);
        e.printStackTrace();
      } catch (Exception e) {
        LOGGER.error("Catch the RE Exception, Recovery the overflow dir: {}, the reason is {}.", childDir.getPath(), e);
      }catch (Throwable t){
        LOGGER.error("Catch the throwable, Recovery the overflow dir: {}, the reason is {}.", childDir.getPath(), t);
      }
    }
  }

  public static void recovery(File overflowChildDir) throws IOException {
    LOGGER.info("Start recovering the overflow {}", overflowChildDir.getPath());
    // overflow child dir 下边有0和1
    OverflowResource workResource;
    OverflowResource mergeResource;
    // 获得子目录下的0或者1
    String[] subFilePaths = clearFile(overflowChildDir.list());
    // 只有一个文件
    if (subFilePaths.length == 1) {
      long count = Long.valueOf(subFilePaths[0]);
      LOGGER.info("Recovery the workDir : {}",
          new File(overflowChildDir.getPath(), String.valueOf(count)).getPath());
      workResource = new OverflowResource(overflowChildDir.getPath(), String.valueOf(count));
      LOGGER
          .info("The overflow processor {} recover from work status.", overflowChildDir.getPath());
    } else {
      long count1 = Long.valueOf(subFilePaths[0]);
      long count2 = Long.valueOf(subFilePaths[1]);
      if (count1 > count2) {
        long temp = count1;
        count1 = count2;
        count2 = temp;
      }
      // work dir > merge dir
      LOGGER.info("Recovery the workDir : {}",
          new File(overflowChildDir.getPath(), String.valueOf(count2)).getPath());
      workResource = new OverflowResource(overflowChildDir.getPath(), String.valueOf(count2));
      LOGGER.info("Recovery the mergeDir : {}",
          new File(overflowChildDir.getPath(), String.valueOf(count2)).getPath());
      mergeResource = new OverflowResource(overflowChildDir.getPath(), String.valueOf(count1));
      LOGGER
          .info("The overflow processor {} recover from merge status.", overflowChildDir.getPath());
    }
    LOGGER.info("End recovering the overflow {}.", overflowChildDir.getPath());
  }

  private static String[] clearFile(String[] subFilePaths) {
    // just clear the files whose name are number.
    List<String> files = new ArrayList<>();
    for (String file : subFilePaths) {
      try {
        Long.valueOf(file);
        files.add(file);
      } catch (NumberFormatException e) {
        // ignore the exception, if the name of file is not a number.
      }
    }
    return files.toArray(new String[files.size()]);
  }
}

