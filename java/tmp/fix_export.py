with open(r"d:\3\code\java\src\main\java\com\cd\controller\ExportController.java", "rb") as f:
    data = f.read()

C = lambda s: s.encode("utf-8")

# Add import
old_import = b"import com.cd.entity.ExportRequest;"
new_import = b"import com.cd.entity.BatchExportRequest;\nimport com.cd.entity.ExportRequest;"
data = data.replace(old_import, new_import)

# Add batch endpoint before the retry endpoint
old_retry = b'    @PostMapping("/export/{taskId}/retry")'
new_endpoint = (
    b'    @PostMapping("/export/batch")\n'
    b'    @PreAuthorize("hasAuthority(\'asset:host:view\')")\n'
    b'    public ExportTask startBatchExport(@RequestBody BatchExportRequest request) {\n'
    b'        AuthenticatedUser user = SecurityUtils.currentUser();\n'
    b'        boolean isAdmin = isAdminUser(user);\n'
    b'        return exportService.startBatchExport(request, user.getUserId(), isAdmin);\n'
    b'    }\n'
    b'\n'
    b'    @PostMapping("/export/{taskId}/retry")'
)
data = data.replace(old_retry, new_endpoint)

with open(r"d:\3\code\java\src\main\java\com\cd\controller\ExportController.java", "wb") as f:
    f.write(data)
print("Controller updated")