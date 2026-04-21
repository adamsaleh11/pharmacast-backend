package ca.pharmaforecast.backend.upload;

import java.util.Map;

public interface UploadBacktestPort {
    Map<String, Object> runUploadBacktest(BacktestUploadRequest request);
}
