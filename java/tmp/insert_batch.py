with open(r"d:\3\code\java\src\main\java\com\cd\server\impl\ExportServiceImpl.java", "rb") as f:
    data = f.read()

# Add import for BatchExportRequest
old_imp = b"import com.cd.entity.ExportRequest;"
new_imp = b"import com.cd.entity.BatchExportRequest;\nimport com.cd.entity.ExportRequest;"
data = data.replace(old_imp, new_imp)

# Read the batch code from temp file
with open(r"d:\3\code\java\tmp\batch_export.java", "rb") as f:
    batch_code = f.read()

# Insert before failTask
marker = b"    private void failTask(ExportTask task, String message) {"
data = data.replace(marker, batch_code + b"\n" + marker)

with open(r"d:\3\code\java\src\main\java\com\cd\server\impl\ExportServiceImpl.java", "wb") as f:
    f.write(data)
print("Batch export methods inserted")