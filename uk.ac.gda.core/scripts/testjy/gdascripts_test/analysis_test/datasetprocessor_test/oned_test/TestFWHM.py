# TestFWHM is implemented using new AbstractDataset - DoubleDataset,not in module TestProcessors which use the deprecated DataSet
import unittest

from gdascripts.analysis.datasetprocessor.oned.FullWidthHalfMaximum import FullWidthHalfMaximum
from uk.ac.diamond.scisoft.analysis.dataset import DoubleDataset
def close(l1, l2):
	for v1, v2 in zip(l1, l2):
		if abs(v1-v2) > .01:
			return False
	return True

def closer(l1, l2, tolerance=0.01):
	for v1, v2 in zip(l1, l2):
		if abs(v1-v2) > tolerance:
			return False
	return True


class Test(unittest.TestCase):
	
	def setUp(self, DataSet=DoubleDataset):
		self.x =    DataSet.array([10.,11.,12.,13.,14.,15.,16.,17.,18.,19.])
		self.peak = DataSet.array([0.,1.,2.,3.,4.,5.,4.,3.,2.,1.])
		self.dip = DataSet.array([5.,4.,3.,2.,1.,0.,1.,2.,3.,4.])
		self.p = None
		
	def check__init__(self, name, labelList, keyxlabel, formatString=''):
		self.assertEqual(self.p.name, name)
		self.assertEqual(self.p.labelList, labelList)
		self.assertEqual(self.p.keyxlabel, keyxlabel)
		if formatString:
			self.assertEqual(self.p.formatString, formatString)
		

class TestFWHM(Test):
	
	def setUp(self):
		Test.setUp(self)
		self.p = FullWidthHalfMaximum()
		
	def test__init__(self):
		self.check__init__('fwhm', ('maxpos','fwhm'), 'maxpos')
		
	def test_process(self):
		pos = 15.0
		fwhm = 5.0

		result = self.p._process(self.x, self.peak)
		expected = (pos, fwhm)
		self.assert_(close(result, expected),"%s\n is not close to expected:\n%s"%(`result`,`expected`))


def suite():
	suite = unittest.TestSuite()
	suite.addTest(unittest.TestLoader().loadTestsFromTestCase(TestFWHM))
	return suite 


if __name__ == '__main__':
	unittest.TextTestRunner(verbosity=2).run(suite())
