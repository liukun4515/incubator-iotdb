package cn.edu.thu.tsfiledb.qp.physical.plan;

import java.util.ArrayList;
import java.util.List;

import cn.edu.thu.tsfile.common.exception.ProcessorException;
import cn.edu.thu.tsfile.timeseries.read.qp.Path;
import cn.edu.thu.tsfiledb.qp.exec.QueryProcessExecutor;
import cn.edu.thu.tsfiledb.qp.logical.operator.Operator.OperatorType;

/**
 * given a delete plan and construct a {@code DeletePlan}
 * 
 * @author kangrong
 *
 */
public class DeletePlan extends PhysicalPlan {
    private long deleteTime;
    private Path path;

    public DeletePlan() {
        super(false, OperatorType.DELETE);
    }

    public DeletePlan(long deleteTime, Path path) {
        super(false, OperatorType.DELETE);
        this.setDeleteTime(deleteTime);
        this.setPath(path);
    }
    @Override
    public boolean processNonQuery(QueryProcessExecutor exec) throws ProcessorException {
        return exec.delete(path, deleteTime);
    }

    public long getDeleteTime() {
        return deleteTime;
    }

    public void setDeleteTime(long delTime) {
        this.deleteTime = delTime;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    @Override
    public List<Path> getInvolvedSeriesPaths() {
        List<Path> ret = new ArrayList<Path>();
        if (path != null)
            ret.add(path);
        return ret;
    }

}