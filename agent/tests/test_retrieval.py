from app.retrieval import RetrievalService


def test_retrieval_filters_by_project_id(tmp_path):
    service = RetrievalService(str(tmp_path))
    service.index_text(1, 10, "a.txt", "alpha project guide")
    service.index_text(2, 20, "b.txt", "beta project guide")

    results = service.search(1, "project guide", limit=5)

    assert any("alpha" in item for item in results)
    assert all("beta" not in item for item in results)


def test_pdf_content_is_indexed(tmp_path):
    service = RetrievalService(str(tmp_path))

    chunks = service.index_file(3, 30, "guide.pdf", build_pdf("Spring Boot PDF guide"))

    stored = service.collection.get(where={"documentId": 30})
    assert chunks == 1
    assert any("Spring Boot PDF guide" in document for document in stored["documents"])


def build_pdf(text: str) -> bytes:
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    stream = f"BT /F1 12 Tf 72 720 Td ({text}) Tj ET".encode("ascii")
    objects.append(b"<< /Length " + str(len(stream)).encode("ascii") + b" >>\nstream\n" + stream + b"\nendstream")

    pdf = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for index, obj in enumerate(objects, start=1):
        offsets.append(len(pdf))
        pdf.extend(f"{index} 0 obj\n".encode("ascii"))
        pdf.extend(obj)
        pdf.extend(b"\nendobj\n")
    xref_offset = len(pdf)
    pdf.extend(f"xref\n0 {len(objects) + 1}\n".encode("ascii"))
    pdf.extend(b"0000000000 65535 f \n")
    for offset in offsets[1:]:
        pdf.extend(f"{offset:010d} 00000 n \n".encode("ascii"))
    pdf.extend(f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode("ascii"))
    return bytes(pdf)
