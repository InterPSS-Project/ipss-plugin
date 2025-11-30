
import pytest

def test_suite():
    pytest.main([
            'test_psse.py', 
            'test_ieee14.py'
        ])
