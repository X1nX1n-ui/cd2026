with open(r"d:\3\code\java\src\main\java\com\cd\server\ExportService.java", "rb") as f:
    data = f.read()

old = b"    ExportTask resumeTask(String taskId);\n}"
new = (
    b"    ExportTask resumeTask(String taskId);\n"
    b"\n"
    b"    ExportTask startBatchExport(com.cd.entity.BatchExportRequest request, Long userId, boolean isAdmin);\n"
    b"}"
)
data = data.replace(old, new)

with open(r"d:\3\code\java\src\main\java\com\cd\server\ExportService.java", "wb") as f:
    f.write(data)
print("ExportService interface updated")