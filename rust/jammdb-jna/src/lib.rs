use jammdb::{Bucket, DB};
use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_uchar};
use std::path::Path;
use std::slice;

const PATH_SEPARATOR: &str = "\u{001f}";

#[no_mangle]
pub extern "C" fn Jammdb_PutValue(
    db_path: *const c_char,
    bucket_path: *const c_char,
    key: *const c_uchar,
    key_len: usize,
    value: *const c_uchar,
    value_len: usize,
) -> *mut c_char {
    run_result(|| {
        let db_path = read_c_string(db_path)?;
        let bucket_names = read_bucket_path(bucket_path)?;
        let key = read_bytes(key, key_len)?;
        let value = read_bytes(value, value_len)?;
        let db = DB::open(Path::new(&db_path))?;
        let tx = db.tx(true)?;
        let bucket = get_bucket_mut(&tx, &bucket_names)?;
        bucket.put(key, value)?;
        tx.commit()?;
        Ok(())
    })
}

#[no_mangle]
pub extern "C" fn Jammdb_DeleteValue(
    db_path: *const c_char,
    bucket_path: *const c_char,
    key: *const c_uchar,
    key_len: usize,
) -> *mut c_char {
    run_result(|| {
        let db_path = read_c_string(db_path)?;
        let bucket_names = read_bucket_path(bucket_path)?;
        let key = read_bytes(key, key_len)?;
        let db = DB::open(Path::new(&db_path))?;
        let tx = db.tx(true)?;
        let bucket = get_bucket_mut(&tx, &bucket_names)?;
        bucket.delete(key)?;
        tx.commit()?;
        Ok(())
    })
}

#[no_mangle]
pub extern "C" fn Jammdb_CreateBucket(
    db_path: *const c_char,
    parent_path: *const c_char,
    bucket_name: *const c_uchar,
    bucket_name_len: usize,
) -> *mut c_char {
    run_result(|| {
        let db_path = read_c_string(db_path)?;
        let parent_names = read_bucket_path(parent_path)?;
        let bucket_name = read_bytes(bucket_name, bucket_name_len)?;
        let db = DB::open(Path::new(&db_path))?;
        let tx = db.tx(true)?;
        if parent_names.is_empty() {
            tx.create_bucket(bucket_name)?;
        } else {
            let parent = get_bucket_mut(&tx, &parent_names)?;
            parent.create_bucket(bucket_name)?;
        }
        tx.commit()?;
        Ok(())
    })
}

#[no_mangle]
pub extern "C" fn Jammdb_DeleteBucket(
    db_path: *const c_char,
    parent_path: *const c_char,
    bucket_name: *const c_uchar,
    bucket_name_len: usize,
) -> *mut c_char {
    run_result(|| {
        let db_path = read_c_string(db_path)?;
        let parent_names = read_bucket_path(parent_path)?;
        let bucket_name = read_bytes(bucket_name, bucket_name_len)?;
        let db = DB::open(Path::new(&db_path))?;
        let tx = db.tx(true)?;
        if parent_names.is_empty() {
            tx.delete_bucket(bucket_name)?;
        } else {
            let parent = get_bucket_mut(&tx, &parent_names)?;
            parent.delete_bucket(bucket_name)?;
        }
        tx.commit()?;
        Ok(())
    })
}

#[no_mangle]
pub extern "C" fn Jammdb_FreeString(message: *mut c_char) {
    if !message.is_null() {
        unsafe {
            drop(CString::from_raw(message));
        }
    }
}

fn run_result<F>(operation: F) -> *mut c_char
where
    F: FnOnce() -> Result<(), Box<dyn std::error::Error>>,
{
    match operation() {
        Ok(()) => std::ptr::null_mut(),
        Err(error) => CString::new(error.to_string())
            .unwrap_or_else(|_| CString::new("jammdb native error").unwrap())
            .into_raw(),
    }
}

fn read_c_string(ptr: *const c_char) -> Result<String, Box<dyn std::error::Error>> {
    if ptr.is_null() {
        return Err("null string pointer".into());
    }
    Ok(unsafe { CStr::from_ptr(ptr) }.to_string_lossy().into_owned())
}

fn read_bucket_path(ptr: *const c_char) -> Result<Vec<Vec<u8>>, Box<dyn std::error::Error>> {
    let path = read_c_string(ptr)?;
    if path.is_empty() {
        return Ok(Vec::new());
    }
    Ok(path
        .split(PATH_SEPARATOR)
        .map(|part| part.as_bytes().to_vec())
        .collect())
}

fn read_bytes<'a>(
    ptr: *const c_uchar,
    len: usize,
) -> Result<&'a [u8], Box<dyn std::error::Error>> {
    if ptr.is_null() && len > 0 {
        return Err("null byte pointer".into());
    }
    Ok(unsafe { slice::from_raw_parts(ptr, len) })
}

fn get_bucket_mut<'b, 'tx>(
    tx: &'b jammdb::Tx<'tx>,
    path: &[Vec<u8>],
) -> Result<Bucket<'b, 'tx>, Box<dyn std::error::Error>> {
    if path.is_empty() {
        return Err("bucket path is required".into());
    }
    let mut bucket = tx.get_bucket(path[0].clone())?;
    for name in &path[1..] {
        bucket = bucket.get_bucket(name.clone())?;
    }
    Ok(bucket)
}
